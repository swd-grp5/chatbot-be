package swdchatbox.modules.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import swdchatbox.modules.ai.config.AiProperties;
import swdchatbox.modules.ai.dto.LlmMessage;
import swdchatbox.modules.ai.dto.LlmResponse;
import swdchatbox.modules.ai.service.EmbeddingService;
import swdchatbox.modules.ai.service.LlmService;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.entity.DocumentChunk;
import swdchatbox.modules.document.enums.DocumentStatus;
import swdchatbox.modules.document.repository.DocumentChunkRepository;
import swdchatbox.modules.document.repository.DocumentRepository;
import swdchatbox.modules.document.repository.DocumentSpecifications;
import swdchatbox.modules.embedding.dto.VectorSearchResult;
import swdchatbox.modules.embedding.service.VectorStoreService;
import swdchatbox.modules.enrollment.service.SubjectEnrollmentService;
import swdchatbox.modules.quiz.QuestionTypeCodes;
import swdchatbox.modules.quiz.dto.request.*;
import swdchatbox.modules.quiz.dto.response.QuizResponse;
import swdchatbox.modules.quiz.entity.QuestionType;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;
import swdchatbox.modules.quiz.repository.QuestionTypeRepository;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizAiGenerationService {

    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final String RETRIEVAL_QUERY = "key concepts definitions facts important topics for examination quiz";

    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final SubjectEnrollmentService subjectEnrollmentService;
    private final UserRepository userRepository;
    private final QuizService quizService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public QuizResponse generate(QuizGenerateRequest request, String userEmail) {
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, request.getSubjectId());
        Subject subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());

        int questionCount = request.getQuestionCount() != null ? request.getQuestionCount() : 5;
        if (questionCount < 1 || questionCount > 20) {
            throw new BadRequestException("questionCount must be between 1 and 20");
        }

        List<DocSource> sources = buildContextSources(subject, request.getDocumentIds());
        if (sources.isEmpty()) {
            throw new BadRequestException("No document content available for this subject. Upload and index documents first.");
        }

        Map<Integer, Document> indexToDocument = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            indexToDocument.put(i + 1, sources.get(i).document());
        }
        String context = renderContext(sources);

        QuestionType questionType = questionTypeRepository.findByCode(QuestionTypeCodes.MULTIPLE_CHOICE)
                .orElseThrow(() -> new ResourceNotFoundException("Multiple choice question type not found"));

        String prompt = buildPrompt(subject, questionCount, context);
        LlmResponse llmResponse;
        try {
            llmResponse = llmService.generate(List.of(
                    LlmMessage.builder().role("system").content(systemPrompt()).build(),
                    LlmMessage.builder().role("user").content(prompt).build()
            ), 0.4, 4096);
        } catch (Exception e) {
            log.error("AI quiz generation failed for subject {}", subject.getId(), e);
            throw new BadRequestException("AI failed to generate quiz. Please try again.");
        }

        QuizCreateRequest createRequest = parseAiResponse(llmResponse.getContent(), request, questionType.getId(), indexToDocument);
        createRequest.setSubjectId(subject.getId());
        if (request.getTimeLimitMinutes() != null) {
            createRequest.setTimeLimitMinutes(request.getTimeLimitMinutes());
        }

        return quizService.saveQuiz(createRequest, userEmail, true);
    }

    /** Nội dung nguồn giữ nguyên theo từng tài liệu để AI có thể đối chiếu và trích nguồn. */
    private record DocSource(Document document, String content) {
    }

    private List<DocSource> buildContextSources(Subject subject, List<UUID> documentIds) {
        List<DocSource> fromVector = retrieveFromVectorStore(subject.getId(), documentIds);
        if (!fromVector.isEmpty()) {
            return fromVector;
        }
        return loadExtractedText(subject.getId(), documentIds);
    }

    private List<DocSource> retrieveFromVectorStore(UUID subjectId, List<UUID> documentIds) {
        try {
            List<Double> vector = embeddingService.embed(RETRIEVAL_QUERY);
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("subjectId", subjectId.toString());
            if (documentIds != null && documentIds.size() == 1) {
                filter.put("documentId", documentIds.get(0).toString());
            }

            List<VectorSearchResult> results = vectorStoreService.search(
                    vector,
                    aiProperties.getRetrievalTopK() * 3,
                    filter);

            if (results.isEmpty()) {
                return List.of();
            }

            Set<UUID> allowedDocIds = documentIds == null || documentIds.isEmpty()
                    ? null
                    : new HashSet<>(documentIds);

            // Gom nội dung theo từng tài liệu, giữ thứ tự xuất hiện đầu tiên.
            Map<UUID, Document> docById = new LinkedHashMap<>();
            Map<UUID, StringBuilder> contentByDoc = new LinkedHashMap<>();

            for (VectorSearchResult result : results) {
                Map<String, Object> metadata = result.getMetadata();
                String chunkIdStr = metadata != null ? (String) metadata.get("chunkId") : null;
                if (chunkIdStr == null) {
                    continue;
                }
                try {
                    UUID chunkId = UUID.fromString(chunkIdStr);
                    Optional<DocumentChunk> chunkOpt = documentChunkRepository.findById(chunkId);
                    if (chunkOpt.isEmpty()) {
                        continue;
                    }
                    DocumentChunk chunk = chunkOpt.get();
                    Document document = chunk.getDocument();
                    if (allowedDocIds != null && !allowedDocIds.contains(document.getId())) {
                        continue;
                    }
                    String content = chunk.getContent();
                    if (content == null || content.isBlank()) {
                        continue;
                    }
                    docById.putIfAbsent(document.getId(), document);
                    contentByDoc.computeIfAbsent(document.getId(), k -> new StringBuilder())
                            .append(content.trim()).append("\n\n");
                } catch (Exception ignored) {
                    // bỏ qua chunk lỗi
                }
            }

            List<DocSource> sources = new ArrayList<>();
            for (Map.Entry<UUID, Document> entry : docById.entrySet()) {
                sources.add(new DocSource(entry.getValue(), contentByDoc.get(entry.getKey()).toString().trim()));
            }
            return sources;
        } catch (Exception e) {
            log.warn("Vector retrieval for quiz generation failed, falling back to extracted text", e);
            return List.of();
        }
    }

    private List<DocSource> loadExtractedText(UUID subjectId, List<UUID> documentIds) {
        Specification<Document> spec = Specification
                .where(DocumentSpecifications.hasSubjectId(subjectId))
                .and(DocumentSpecifications.hasActive(true))
                .and((root, query, cb) -> cb.equal(root.get("status"), DocumentStatus.INDEXED));

        List<Document> documents = documentRepository.findAll(spec);
        if (documentIds != null && !documentIds.isEmpty()) {
            Set<UUID> allowed = new HashSet<>(documentIds);
            documents = documents.stream().filter(d -> allowed.contains(d.getId())).toList();
        }

        List<DocSource> sources = new ArrayList<>();
        for (Document document : documents) {
            if (document.getExtractedText() != null && !document.getExtractedText().isBlank()) {
                sources.add(new DocSource(document, document.getExtractedText().trim()));
            }
        }
        return sources;
    }

    /** Render context có nhãn [DOC i] cho từng tài liệu, chia đều ngân sách ký tự. */
    private String renderContext(List<DocSource> sources) {
        int budgetPerDoc = Math.max(500, MAX_CONTEXT_CHARS / sources.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            DocSource source = sources.get(i);
            String body = source.content();
            if (body.length() > budgetPerDoc) {
                body = body.substring(0, budgetPerDoc);
            }
            sb.append("[DOC ").append(i + 1).append("] ")
                    .append(source.document().getTitle()).append("\n")
                    .append(body).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String systemPrompt() {
        return """
                You are an expert educator creating multiple-choice quizzes from course materials.
                Return ONLY valid JSON (no markdown fences, no commentary).
                Each question must be multiple choice with mode SINGLE (exactly 1 correct) or MULTIPLE (1+ correct).
                Use Vietnamese for question and option text when the source material is in Vietnamese.
                """;
    }

    private String buildPrompt(Subject subject, int questionCount, String context) {
        return """
                Create a quiz for subject: %s (%s)
                Number of questions: %d
                Mix SINGLE and MULTIPLE choice questions.

                JSON schema:
                {
                  "title": "string",
                  "description": "string",
                  "questions": [
                    {
                      "multipleChoiceMode": "SINGLE or MULTIPLE",
                      "questionText": "string",
                      "points": 1,
                      "sourceDocumentIndex": 1,
                      "sourceExcerpt": "the exact sentence(s) from the material that support the correct answer",
                      "options": [
                        { "optionText": "string", "isCorrect": true }
                      ]
                    }
                  ]
                }

                Rules:
                - SINGLE: exactly 1 option with isCorrect=true, at least 4 options
                - MULTIPLE: at least 2 options with isCorrect=true, at least 4 options
                - Base questions ONLY on the provided material
                - Each document below is labeled as [DOC n]. For every question set "sourceDocumentIndex" to the number n of the document it comes from, and "sourceExcerpt" to the exact quote from that document justifying the correct answer.

                MATERIAL:
                %s
                """.formatted(subject.getName(), subject.getCode(), questionCount, context);
    }

    private QuizCreateRequest parseAiResponse(String raw, QuizGenerateRequest request, UUID questionTypeId, Map<Integer, Document> indexToDocument) {
        try {
            String json = stripMarkdownFence(raw);
            JsonNode root = objectMapper.readTree(json);

            String title = request.getTitle() != null && !request.getTitle().isBlank()
                    ? request.getTitle().trim()
                    : root.path("title").asText("Quiz AI");
            String description = request.getDescription() != null
                    ? request.getDescription()
                    : root.path("description").asText(null);

            List<QuizQuestionRequest> questions = new ArrayList<>();
            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray() || questionsNode.isEmpty()) {
                throw new BadRequestException("AI returned no questions");
            }

            int sortOrder = 0;
            for (JsonNode qNode : questionsNode) {
                MultipleChoiceMode mode = MultipleChoiceMode.valueOf(
                        qNode.path("multipleChoiceMode").asText("SINGLE").toUpperCase());
                if (mode != MultipleChoiceMode.SINGLE && mode != MultipleChoiceMode.MULTIPLE) {
                    mode = MultipleChoiceMode.SINGLE;
                }

                QuizQuestionRequest question = new QuizQuestionRequest();
                question.setQuestionTypeId(questionTypeId);
                question.setMultipleChoiceMode(mode);
                question.setQuestionText(qNode.path("questionText").asText());
                question.setPoints(qNode.path("points").asInt(1));
                question.setSortOrder(sortOrder++);

                int docIndex = qNode.path("sourceDocumentIndex").asInt(0);
                Document sourceDoc = indexToDocument.get(docIndex);
                if (sourceDoc != null) {
                    question.setSourceDocumentId(sourceDoc.getId());
                }
                String excerpt = qNode.path("sourceExcerpt").asText(null);
                if (excerpt != null && !excerpt.isBlank()) {
                    question.setSourceExcerpt(excerpt.trim());
                }

                List<QuizOptionRequest> options = new ArrayList<>();
                int optionOrder = 0;
                for (JsonNode optNode : qNode.path("options")) {
                    QuizOptionRequest option = new QuizOptionRequest();
                    option.setOptionText(optNode.path("optionText").asText());
                    option.setIsCorrect(optNode.path("isCorrect").asBoolean(false));
                    option.setSortOrder(optionOrder++);
                    options.add(option);
                }
                question.setOptions(options);
                questions.add(question);
            }

            QuizCreateRequest createRequest = new QuizCreateRequest();
            createRequest.setTitle(title);
            createRequest.setDescription(description);
            createRequest.setQuestions(questions);
            return createRequest;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI quiz JSON: {}", raw, e);
            throw new BadRequestException("AI returned invalid quiz format. Please try again.");
        }
    }

    private String stripMarkdownFence(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n?", "");
            trimmed = trimmed.replaceFirst("\\n?```$", "");
        }
        return trimmed.trim();
    }

    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("Authenticated user is required");
        }
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
