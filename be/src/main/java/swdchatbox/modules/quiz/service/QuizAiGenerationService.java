package swdchatbox.modules.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import swdchatbox.modules.ai.dto.LlmMessage;
import swdchatbox.modules.ai.dto.LlmResponse;
import swdchatbox.modules.ai.service.EmbeddingService;
import swdchatbox.modules.ai.service.LlmService;
import swdchatbox.modules.setting.service.ModelSettingService;
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
import swdchatbox.modules.quiz.dto.response.BankQuestionResponse;
import swdchatbox.modules.quiz.dto.response.QuizResponse;
import swdchatbox.modules.quiz.entity.QuestionType;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;
import swdchatbox.modules.quiz.enums.PointsDistributionMode;
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
    /** ~450 output tokens per MC question (text + 4 options + short excerpt) + overhead. */
    private static final int TOKENS_PER_QUESTION = 450;
    private static final int TOKEN_OVERHEAD = 1_024;
    private static final int MIN_OUTPUT_TOKENS = 4_096;
    private static final int MAX_OUTPUT_TOKENS = 8_192;
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
    private final BankQuestionService bankQuestionService;
    private final ModelSettingService modelSettingService;
    private final ObjectMapper objectMapper;
    private final swdchatbox.modules.credit.service.CreditService creditService;

    public QuizResponse generate(QuizGenerateRequest request, String userEmail) {
        validateGenerateRequest(request);

        User lecturer = resolveUser(userEmail);
        creditService.ensureEnough(lecturer, "QUIZ_GENERATE");
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, request.getSubjectId());
        Subject subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());

        int questionCount = request.getQuestionCount() != null ? request.getQuestionCount() : 5;
        int totalPoints = request.getTotalPoints() != null ? request.getTotalPoints() : 10;
        PointsDistributionMode distribution = request.getPointsDistribution() != null
                ? request.getPointsDistribution()
                : PointsDistributionMode.EVEN;

        if (totalPoints < questionCount * 0.1) {
            throw new BadRequestException(
                    "totalPoints is too small for questionCount; need at least 0.1 points per question");
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

        String prompt = buildPrompt(subject, questionCount, totalPoints, distribution, context);
        int maxTokens = resolveMaxOutputTokens(questionCount);
        var aiConfig = modelSettingService.resolveEffectiveConfig();
        LlmResponse llmResponse;
        try {
            llmResponse = llmService.generate(List.of(
                    LlmMessage.builder().role("system").content(systemPrompt()).build(),
                    LlmMessage.builder().role("user").content(prompt).build()
            ), 0.4, maxTokens);
        } catch (Exception e) {
            log.error(
                    "[quiz] step=llm.generate subjectId={} provider={} chatModel={} maxTokens={} error={}",
                    subject.getId(),
                    aiConfig.getProvider(),
                    aiConfig.getChatModel(),
                    maxTokens,
                    e.getMessage(),
                    e);
            throw new BadRequestException("AI failed to generate quiz. Please try again.");
        }

        if (llmResponse == null || llmResponse.getContent() == null || llmResponse.getContent().isBlank()) {
            throw new BadRequestException("AI returned empty quiz content. Please try again.");
        }

        QuizCreateRequest createRequest = parseAiResponse(
                llmResponse.getContent(), request, questionType.getId(), indexToDocument, totalPoints, distribution);
        createRequest.setSubjectId(subject.getId());
        if (request.getTimeLimitMinutes() != null) {
            createRequest.setTimeLimitMinutes(request.getTimeLimitMinutes());
        }

        // Charge only after a parseable quiz is produced (failed parses used to burn credits on retries).
        creditService.consume(lecturer, "QUIZ_GENERATE");
        return quizService.saveQuiz(createRequest, userEmail, true, request.getAllowRetake());
    }

    /**
     * AI sinh câu hỏi trắc nghiệm trực tiếp vào ngân hàng câu hỏi (không tạo quiz).
     * Tái sử dụng cùng luồng RAG + prompt + parse như sinh quiz.
     */
    public List<BankQuestionResponse> generateIntoBank(BankQuestionGenerateRequest request, String userEmail) {
        if (request == null || request.getSubjectId() == null) {
            throw new BadRequestException("subjectId is required");
        }
        int questionCount = request.getQuestionCount() != null ? request.getQuestionCount() : 5;
        if (questionCount < 1 || questionCount > 20) {
            throw new BadRequestException("questionCount must be between 1 and 20");
        }
        if (request.getDocumentIds() != null) {
            for (int i = 0; i < request.getDocumentIds().size(); i++) {
                if (request.getDocumentIds().get(i) == null) {
                    throw new BadRequestException("documentIds[" + i + "] must not be null");
                }
            }
        }

        User lecturer = resolveUser(userEmail);
        creditService.ensureEnough(lecturer, "QUIZ_GENERATE");
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, request.getSubjectId());
        Subject subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());

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

        int totalPoints = 10;
        PointsDistributionMode distribution = PointsDistributionMode.EVEN;
        String prompt = buildPrompt(subject, questionCount, totalPoints, distribution, context);
        int maxTokens = resolveMaxOutputTokens(questionCount);
        var aiConfig = modelSettingService.resolveEffectiveConfig();
        LlmResponse llmResponse;
        try {
            llmResponse = llmService.generate(List.of(
                    LlmMessage.builder().role("system").content(systemPrompt()).build(),
                    LlmMessage.builder().role("user").content(prompt).build()
            ), 0.4, maxTokens);
        } catch (Exception e) {
            log.error(
                    "[question-bank] step=llm.generate subjectId={} provider={} chatModel={} maxTokens={} error={}",
                    subject.getId(),
                    aiConfig.getProvider(),
                    aiConfig.getChatModel(),
                    maxTokens,
                    e.getMessage(),
                    e);
            throw new BadRequestException("AI failed to generate questions. Please try again.");
        }

        if (llmResponse == null || llmResponse.getContent() == null || llmResponse.getContent().isBlank()) {
            throw new BadRequestException("AI returned empty content. Please try again.");
        }

        QuizGenerateRequest proxy = new QuizGenerateRequest();
        proxy.setSubjectId(subject.getId());
        proxy.setQuestionCount(questionCount);
        QuizCreateRequest parsed = parseAiResponse(
                llmResponse.getContent(), proxy, questionType.getId(), indexToDocument, totalPoints, distribution);

        creditService.consume(lecturer, "QUIZ_GENERATE");
        return bankQuestionService.saveGenerated(subject, lecturer, parsed.getQuestions(), request.getDefaultPoints());
    }

    private static int resolveMaxOutputTokens(int questionCount) {
        int estimated = questionCount * TOKENS_PER_QUESTION + TOKEN_OVERHEAD;
        return Math.min(MAX_OUTPUT_TOKENS, Math.max(MIN_OUTPUT_TOKENS, estimated));
    }

    private void validateGenerateRequest(QuizGenerateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.getSubjectId() == null) {
            throw new BadRequestException("subjectId is required");
        }

        Integer questionCount = request.getQuestionCount();
        if (questionCount != null && (questionCount < 1 || questionCount > 20)) {
            throw new BadRequestException("questionCount must be between 1 and 20");
        }

        Integer totalPoints = request.getTotalPoints();
        if (totalPoints != null && (totalPoints < 1 || totalPoints > 10)) {
            throw new BadRequestException("totalPoints must be between 1 and 10");
        }

        Integer timeLimit = request.getTimeLimitMinutes();
        if (timeLimit != null && (timeLimit < 1 || timeLimit > 600)) {
            throw new BadRequestException("timeLimitMinutes must be between 1 and 600");
        }

        if (request.getDocumentIds() != null) {
            for (int i = 0; i < request.getDocumentIds().size(); i++) {
                if (request.getDocumentIds().get(i) == null) {
                    throw new BadRequestException("documentIds[" + i + "] must not be null");
                }
            }
        }
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
                    modelSettingService.resolveEffectiveConfig().getTopK() * 3,
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
            var aiConfig = modelSettingService.resolveEffectiveConfig();
            log.warn(
                    "[quiz] step=vector-retrieval subjectId={} provider={} embeddingModel={} error={} — falling back to extracted text",
                    subjectId,
                    aiConfig.getProvider(),
                    aiConfig.getEmbeddingModel(),
                    e.getMessage(),
                    e);
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
                Keep questionText, optionText, and sourceExcerpt concise so the full JSON fits in one response.
                """;
    }

    private String buildPrompt(Subject subject, int questionCount, int totalPoints,
                               PointsDistributionMode distribution, String context) {
        String pointsRule = distribution == PointsDistributionMode.BY_DIFFICULTY
                ? """
                - Assign difficulty EASY, MEDIUM, or HARD for each question (mix difficulties).
                - Do NOT set final points yourself; backend will allocate points from totalPoints by difficulty \
                (EASY < MEDIUM < HARD, 2 decimal places e.g. 0.14).
                """
                : """
                - Do NOT set final points yourself; backend will split totalPoints as evenly as possible \
                across questions (2 decimal places e.g. 0.14 / 0.15 when 1 point / 7 questions).
                - Still include "difficulty": "MEDIUM" for every question (placeholder).
                """;

        return """
                Create a quiz for subject: %s (%s)
                Number of questions: %d
                Total points for the whole quiz: %d
                Points distribution mode: %s
                Mix SINGLE and MULTIPLE choice questions.

                JSON schema:
                {
                  "title": "string",
                  "description": "string",
                  "questions": [
                    {
                      "multipleChoiceMode": "SINGLE or MULTIPLE",
                      "questionText": "string",
                      "difficulty": "EASY or MEDIUM or HARD",
                      "sourceDocumentIndex": 1,
                      "sourceExcerpt": "the exact sentence(s) from the material that support the correct answer",
                      "options": [
                        { "optionText": "string", "isCorrect": true }
                      ]
                    }
                  ]
                }

                Rules:
                - Return exactly %d questions in the "questions" array
                - SINGLE: exactly 1 option with isCorrect=true, at least 4 options
                - MULTIPLE: at least 2 options with isCorrect=true, at least 4 options
                - Keep optionText short (one line). Keep sourceExcerpt to at most 1-2 short sentences.
                - Base questions ONLY on the provided material
                - Each document below is labeled as [DOC n]. For every question set "sourceDocumentIndex" to the number n of the document it comes from, and "sourceExcerpt" to the exact quote from that document justifying the correct answer.
                - Close every brace/bracket; the JSON must be complete and valid.
                %s
                MATERIAL:
                %s
                """.formatted(
                subject.getName(),
                subject.getCode(),
                questionCount,
                totalPoints,
                distribution.name(),
                questionCount,
                pointsRule,
                context);
    }

    private QuizCreateRequest parseAiResponse(String raw, QuizGenerateRequest request, UUID questionTypeId,
                                              Map<Integer, Document> indexToDocument, int totalPoints,
                                              PointsDistributionMode distribution) {
        try {
            String json = extractJson(raw);
            if (!looksLikeCompleteJsonObject(json)) {
                throw new BadRequestException(
                        "AI response was truncated before the quiz JSON finished. Try fewer questions or try again.");
            }
            JsonNode root = objectMapper.readTree(json);

            String title = request.getTitle() != null && !request.getTitle().isBlank()
                    ? request.getTitle().trim()
                    : root.path("title").asText("Quiz AI");
            String description = request.getDescription() != null
                    ? request.getDescription()
                    : root.path("description").asText(null);

            List<QuizQuestionRequest> questions = new ArrayList<>();
            List<String> difficulties = new ArrayList<>();
            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray() || questionsNode.isEmpty()) {
                throw new BadRequestException("AI returned no questions");
            }

            int sortOrder = 0;
            int questionIndex = 0;
            for (JsonNode qNode : questionsNode) {
                if (qNode == null || qNode.isNull()) {
                    throw new BadRequestException("AI returned a null question at index " + questionIndex);
                }

                String questionText = qNode.path("questionText").asText(null);
                if (questionText == null || questionText.isBlank()) {
                    throw new BadRequestException("AI question at index " + questionIndex + " is missing questionText");
                }

                JsonNode optionsNode = qNode.path("options");
                if (!optionsNode.isArray() || optionsNode.isEmpty()) {
                    throw new BadRequestException("AI question at index " + questionIndex + " has no options");
                }

                MultipleChoiceMode mode;
                try {
                    mode = MultipleChoiceMode.valueOf(
                            qNode.path("multipleChoiceMode").asText("SINGLE").toUpperCase());
                } catch (IllegalArgumentException ex) {
                    mode = MultipleChoiceMode.SINGLE;
                }
                if (mode != MultipleChoiceMode.SINGLE && mode != MultipleChoiceMode.MULTIPLE) {
                    mode = MultipleChoiceMode.SINGLE;
                }

                QuizQuestionRequest question = new QuizQuestionRequest();
                question.setQuestionTypeId(questionTypeId);
                question.setMultipleChoiceMode(mode);
                question.setQuestionText(questionText.trim());
                question.setPoints(1.0); // placeholder; overwritten by distributePoints
                question.setSortOrder(sortOrder++);
                difficulties.add(normalizeDifficulty(qNode.path("difficulty").asText("MEDIUM")));

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
                int correctCount = 0;
                for (int optIdx = 0; optIdx < optionsNode.size(); optIdx++) {
                    JsonNode optNode = optionsNode.get(optIdx);
                    if (optNode == null || optNode.isNull()) {
                        throw new BadRequestException(
                                "AI question at index " + questionIndex + " has a null option at index " + optIdx);
                    }
                    String optionText = optNode.path("optionText").asText(null);
                    if (optionText == null || optionText.isBlank()) {
                        throw new BadRequestException(
                                "AI question at index " + questionIndex + " option " + optIdx + " is missing optionText");
                    }
                    if (!optNode.has("isCorrect") || optNode.get("isCorrect").isNull()) {
                        throw new BadRequestException(
                                "AI question at index " + questionIndex + " option " + optIdx + " is missing isCorrect");
                    }

                    QuizOptionRequest option = new QuizOptionRequest();
                    option.setOptionText(optionText.trim());
                    boolean isCorrect = optNode.path("isCorrect").asBoolean(false);
                    option.setIsCorrect(isCorrect);
                    option.setSortOrder(optionOrder++);
                    if (isCorrect) {
                        correctCount++;
                    }
                    options.add(option);
                }

                if (options.size() < 2) {
                    throw new BadRequestException(
                            "AI question at index " + questionIndex + " must have at least 2 options");
                }
                if (mode == MultipleChoiceMode.SINGLE && correctCount != 1) {
                    throw new BadRequestException(
                            "AI question at index " + questionIndex + " (SINGLE) must have exactly 1 correct option");
                }
                if (mode == MultipleChoiceMode.MULTIPLE && correctCount < 1) {
                    throw new BadRequestException(
                            "AI question at index " + questionIndex + " (MULTIPLE) must have at least 1 correct option");
                }

                question.setOptions(options);
                questions.add(question);
                questionIndex++;
            }

            if (questions.isEmpty()) {
                throw new BadRequestException("AI returned no valid questions");
            }

            distributePoints(questions, difficulties, totalPoints, distribution);

            String resolvedTitle = title != null && !title.isBlank() ? title.trim() : "Quiz AI";

            QuizCreateRequest createRequest = new QuizCreateRequest();
            createRequest.setTitle(resolvedTitle);
            createRequest.setDescription(description);
            createRequest.setQuestions(questions);
            return createRequest;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI quiz JSON (len={}): {}",
                    raw == null ? 0 : raw.length(),
                    raw == null ? "null" : raw.substring(0, Math.min(800, raw.length())),
                    e);
            String trimmed = raw == null ? "" : raw.trim();
            if (!trimmed.isEmpty() && !trimmed.endsWith("}") && !trimmed.endsWith("```")) {
                throw new BadRequestException(
                        "AI response was truncated before the quiz JSON finished. Try fewer questions or try again.");
            }
            throw new BadRequestException("AI returned invalid quiz format. Please try again.");
        }
    }

    /**
     * Gán điểm từng câu sao cho tổng đúng bằng {@code totalPoints} (2 chữ số thập phân).
     * EVEN: chia đều nhất có thể (largest remainder). BY_DIFFICULTY: trọng số EASY=1, MEDIUM=1.5, HARD=2.
     * Ví dụ EVEN 7 câu / 1 điểm → 0.14 hoặc 0.15, không dồn phần dư vào câu cuối.
     */
    private void distributePoints(List<QuizQuestionRequest> questions, List<String> difficulties,
                                  int totalPoints, PointsDistributionMode distribution) {
        int n = questions.size();
        if (n == 0) {
            return;
        }

        double[] weights = new double[n];
        if (distribution == PointsDistributionMode.BY_DIFFICULTY) {
            for (int i = 0; i < n; i++) {
                weights[i] = switch (difficulties.get(i)) {
                    case "EASY" -> 1.0;
                    case "HARD" -> 2.0;
                    default -> 1.5; // MEDIUM
                };
            }
        } else {
            Arrays.fill(weights, 1.0);
        }

        double weightSum = Arrays.stream(weights).sum();
        // Làm việc trên centi-points (0.01) để tổng đúng tuyệt đối.
        int totalCents = totalPoints * 100;
        int[] cents = new int[n];
        double[] fractions = new double[n];
        int assigned = 0;

        for (int i = 0; i < n; i++) {
            double exact = totalCents * weights[i] / weightSum;
            cents[i] = (int) Math.floor(exact);
            fractions[i] = exact - cents[i];
            assigned += cents[i];
        }

        int leftover = totalCents - assigned;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int cmp = Double.compare(fractions[b], fractions[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        for (int k = 0; k < leftover; k++) {
            cents[order[k]]++;
        }

        // Đảm bảo tối thiểu 0.1; nếu bị ép lên thì lấy từ câu có điểm cao nhất.
        for (int i = 0; i < n; i++) {
            if (cents[i] >= 10) {
                continue;
            }
            int need = 10 - cents[i];
            cents[i] = 10;
            while (need > 0) {
                int donor = -1;
                for (int j = 0; j < n; j++) {
                    if (j != i && cents[j] > 10 && (donor < 0 || cents[j] > cents[donor])) {
                        donor = j;
                    }
                }
                if (donor < 0) {
                    break;
                }
                cents[donor]--;
                need--;
            }
        }

        for (int i = 0; i < n; i++) {
            questions.get(i).setPoints(cents[i] / 100.0);
        }
    }

    private static String normalizeDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MEDIUM";
        }
        return switch (raw.trim().toUpperCase()) {
            case "EASY" -> "EASY";
            case "HARD" -> "HARD";
            default -> "MEDIUM";
        };
    }

    private String extractJson(String raw) {
        String trimmed = stripMarkdownFence(raw);
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static boolean looksLikeCompleteJsonObject(String json) {
        if (json == null) {
            return false;
        }
        String trimmed = json.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
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
