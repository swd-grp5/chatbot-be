package swdchatbox.modules.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import swdchatbox.modules.ai.config.AiProperties;
import swdchatbox.modules.ai.dto.LlmMessage;
import swdchatbox.modules.ai.dto.LlmResponse;
import swdchatbox.modules.ai.service.EmbeddingService;
import swdchatbox.modules.ai.service.LlmService;
import swdchatbox.modules.setting.dto.EffectiveAiConfig;
import swdchatbox.modules.setting.service.ModelSettingService;
import swdchatbox.modules.chat.dto.request.CreateConversationRequest;
import swdchatbox.modules.chat.dto.request.SendMessageRequest;
import swdchatbox.modules.chat.dto.request.UpdateConversationRequest;
import swdchatbox.modules.chat.dto.response.*;
import swdchatbox.modules.chat.entity.ChatConversation;
import swdchatbox.modules.chat.entity.ChatMessage;
import swdchatbox.modules.chat.enums.MessageRole;
import swdchatbox.modules.chat.repository.ChatConversationRepository;
import swdchatbox.modules.chat.repository.ChatMessageRepository;
import swdchatbox.modules.citation.entity.MessageCitation;
import swdchatbox.modules.citation.repository.MessageCitationRepository;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.entity.DocumentChunk;
import swdchatbox.modules.enrollment.service.SubjectEnrollmentService;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.document.repository.DocumentChunkRepository;
import swdchatbox.modules.document.repository.DocumentRepository;
import swdchatbox.modules.subject.repository.SubjectRepository;
import swdchatbox.modules.embedding.dto.VectorSearchResult;
import swdchatbox.modules.embedding.service.VectorStoreService;
import swdchatbox.modules.user.entity.User;

import java.util.*;

/**
 * Main service orchestrating the RAG (Retrieval-Augmented Generation) pipeline.
 *
 * Flow: User question → Embed query → Vector search → Build prompt → LLM
 * generate → Save + return
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectEnrollmentService subjectEnrollmentService;

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final LlmService llmService;
    private final PromptBuilder promptBuilder;
    private final AiProperties aiProperties;
    private final ModelSettingService modelSettingService;
    private final ObjectMapper objectMapper;
    private final swdchatbox.modules.credit.service.CreditService creditService;

    // ───────────────── Conversation CRUD ─────────────────

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request, User user) {
        Subject subject = null;
        if (request.getSubjectId() != null) {
            subjectEnrollmentService.requireStudentCanAccessSubject(user, request.getSubjectId());
            subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());
        }

        ChatConversation conversation = ChatConversation.builder()
                .user(user)
                .subject(subject)
                .title(request.getTitle())
                .selectedDocumentIdsJson(serializeDocumentIds(request.getDocumentIds()))
                .totalMessages(0)
                .active(true)
                .build();

        conversation = conversationRepository.save(conversation);
        return toConversationResponse(conversation);
    }

    public Page<ConversationResponse> getConversations(UUID userId, Pageable pageable) {
        return conversationRepository
                .findAllByUser_IdAndActiveTrueOrderByUpdatedAtDesc(userId, pageable)
                .map(this::toConversationResponse);
    }

    public ConversationResponse getConversation(UUID conversationId, UUID userId) {
        ChatConversation conversation = findConversation(conversationId, userId);
        return toConversationResponse(conversation);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        ChatConversation conversation = findConversation(conversationId, userId);
        conversation.setActive(false);
        conversationRepository.save(conversation);
    }

    @Transactional
    public ConversationResponse updateConversation(UUID conversationId, UUID userId,
            UpdateConversationRequest request) {
        ChatConversation conversation = findConversation(conversationId, userId);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            conversation.setTitle(request.getTitle().trim());
        }
        if (request.getDocumentIds() != null) {
            conversation.setSelectedDocumentIdsJson(serializeDocumentIds(request.getDocumentIds()));
        }

        conversation = conversationRepository.save(conversation);
        return toConversationResponse(conversation);
    }

    // ───────────────── Message History ─────────────────

    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable) {
        // Verify ownership
        findConversation(conversationId, userId);

        return messageRepository
                .findAllByConversation_IdOrderByCreatedAtAsc(conversationId, pageable)
                .map(this::toMessageResponse);
    }

    // ───────────────── RAG Pipeline: Send Message ─────────────────

    @Transactional
    public ChatAnswerResponse sendMessage(UUID conversationId, SendMessageRequest request, User user) {
        creditService.ensureEnough(user, "CHAT_QUESTION");
        ChatConversation conversation = findConversation(conversationId, user.getId());
        String userQuestion = request.getMessage();

        // 1. Save user message
        ChatMessage userMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(userQuestion)
                .build();
        userMessage = messageRepository.save(userMessage);
        final UUID savedUserMessageId = userMessage.getId();

        // Resolve AI config early so failure logs can name provider/model
        EffectiveAiConfig aiConfig = modelSettingService.resolveEffectiveConfig();

        // 2. Embed the user question
        List<Double> queryVector;
        try {
            queryVector = embeddingService.embed(userQuestion);
        } catch (Exception e) {
            log.error(
                    "[chat] step=embed-query conversationId={} provider={} embeddingModel={} error={}",
                    conversationId,
                    aiConfig.getProvider(),
                    aiConfig.getEmbeddingModel(),
                    e.getMessage(),
                    e);
            return buildErrorResponse(conversation, userMessage,
                    "Không thể xử lý câu hỏi. Vui lòng thử lại.",
                    aiConfig.getChatModel());
        }

        // 3–4. Title-aware retrieval (+ summary fallback for vague questions)
        List<UUID> selectedDocIds = parseDocumentIds(conversation.getSelectedDocumentIdsJson());
        List<Document> attachedDocs = loadAttachedDocuments(selectedDocIds);
        List<Document> titleMatchedDocs = matchDocumentsByTitle(userQuestion, attachedDocs);
        boolean summaryIntent = isSummaryIntent(userQuestion);

        List<UUID> retrievalDocIds = !titleMatchedDocs.isEmpty()
                ? titleMatchedDocs.stream().map(Document::getId).toList()
                : selectedDocIds;
        Map<String, Object> filter = buildSearchFilter(conversation, retrievalDocIds);

        log.info(
                "[chat] step=retrieve conversationId={} selectedDocIds={} titleMatched={} summaryIntent={} filter={}",
                conversationId,
                selectedDocIds,
                titleMatchedDocs.stream().map(Document::getTitle).toList(),
                summaryIntent,
                filter);

        List<PromptBuilder.RetrievedChunk> retrievedChunks;
        List<Document> summaryTargets = resolveSummaryTargets(
                userQuestion, titleMatchedDocs, attachedDocs, summaryIntent);
        if (!summaryTargets.isEmpty()) {
            // Vague / title-only questions → load representative chunks (skip weak semantic match)
            retrievedChunks = loadRepresentativeChunks(summaryTargets, Math.max(aiConfig.getTopK() * 2, 8));
            log.info("[chat] step=summary-fallback conversationId={} docs={} chunks={}",
                    conversationId,
                    summaryTargets.stream().map(Document::getTitle).toList(),
                    retrievedChunks.size());
        } else {
            List<VectorSearchResult> searchResults;
            try {
                // When scoped to the user's attached docs we keep the top-K best chunks
                // regardless of the default threshold — cross-lingual (VN query / EN doc)
                // cosine scores are often below 0.5 yet still the right chunks.
                boolean scoped = (retrievalDocIds != null && !retrievalDocIds.isEmpty());
                double threshold = scoped ? 0.0 : aiProperties.getRetrievalScoreThreshold();
                searchResults = vectorStoreService.search(
                        queryVector,
                        aiConfig.getTopK(),
                        filter,
                        threshold);
            } catch (Exception e) {
                log.error(
                        "[chat] step=vector-search conversationId={} provider={} embeddingModel={} topK={} error={}",
                        conversationId,
                        aiConfig.getProvider(),
                        aiConfig.getEmbeddingModel(),
                        aiConfig.getTopK(),
                        e.getMessage(),
                        e);
                searchResults = List.of();
            }

            retrievedChunks = enrichSearchResults(searchResults);

            // Hybrid: merge in lexical keyword matches so literal terms
            // ("duration", "deadline") are found even when embeddings miss.
            List<Document> lexicalScope = !titleMatchedDocs.isEmpty() ? titleMatchedDocs : attachedDocs;
            retrievedChunks = mergeWithLexicalMatches(
                    retrievedChunks, userQuestion, lexicalScope, aiConfig.getTopK());

            // If still nothing and we matched a title, fall back to representative chunks
            if (retrievedChunks.isEmpty() && !titleMatchedDocs.isEmpty()) {
                retrievedChunks = loadRepresentativeChunks(titleMatchedDocs, Math.max(aiConfig.getTopK() * 2, 8));
                log.info("[chat] step=title-fallback conversationId={} chunks={}",
                        conversationId, retrievedChunks.size());
            }
        }

        // Hard guard: no grounded content → return deterministic message, never let the LLM invent.
        if (retrievedChunks.isEmpty()) {
            log.info("[chat] step=no-context conversationId={} — returning not-found without calling LLM",
                    conversationId);
            String notFound = attachedDocs.isEmpty()
                    ? "Đoạn chat này chưa gắn tài liệu nào, nên mình chưa có nội dung để trả lời. "
                            + "Bạn hãy gắn tài liệu vào chat rồi hỏi lại nhé."
                    : "Xin lỗi, mình không tìm thấy nội dung phù hợp trong các tài liệu đã gắn để trả lời câu hỏi này.";
            return buildErrorResponse(conversation, userMessage, notFound, aiConfig.getChatModel());
        }

        // 5. Load conversation history (lấy trực tiếp theo thứ tự tăng dần, bỏ message
        // vừa lưu)
        List<ChatMessage> allHistory = messageRepository
                .findAllByConversation_IdOrderByCreatedAtAsc(conversationId);
        // Loại bỏ user message vừa lưu (tin nhắn cuối cùng)
        List<ChatMessage> history = allHistory.stream()
                .filter(m -> !m.getId().equals(savedUserMessageId))
                .toList();
        // Giới hạn số tin nhắn lịch sử (conversationHistoryLimit * 2: user + assistant)
        int historyLimit = aiProperties.getConversationHistoryLimit() * 2;
        if (history.size() > historyLimit) {
            history = history.subList(history.size() - historyLimit, history.size());
        }

        // 6. Build prompt (include attached doc titles so meta-questions stay accurate)
        List<String> attachedTitles = resolveDocumentTitles(selectedDocIds);
        List<LlmMessage> llmMessages = promptBuilder.buildMessages(
                retrievedChunks,
                history,
                userQuestion,
                aiProperties.getConversationHistoryLimit(),
                attachedTitles);

        // 7. Call LLM
        LlmResponse llmResponse;
        try {
            llmResponse = llmService.generate(llmMessages);
        } catch (Exception e) {
            log.error(
                    "[chat] step=llm.generate conversationId={} provider={} chatModel={} messageCount={} error={}",
                    conversationId,
                    aiConfig.getProvider(),
                    aiConfig.getChatModel(),
                    llmMessages.size(),
                    e.getMessage(),
                    e);
            return buildErrorResponse(conversation, userMessage,
                    "Không thể tạo câu trả lời. Vui lòng thử lại.",
                    aiConfig.getChatModel());
        }

        // Consume credit since LLM generation succeeded!
        creditService.consume(user, "CHAT_QUESTION");

        // 8. Save assistant message (append nguồn so FE luôn hiện tên file)
        String answerContent = appendSourceFooter(llmResponse.getContent(), retrievedChunks);
        ChatMessage assistantMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(answerContent)
                .llmModel(llmResponse.getModel())
                .promptTokens(llmResponse.getPromptTokens())
                .completionTokens(llmResponse.getCompletionTokens())
                .totalTokens(llmResponse.getTotalTokens())
                .build();
        assistantMessage = messageRepository.save(assistantMessage);

        // 9. Save citations
        List<CitationResponse> citationResponses = saveCitations(assistantMessage, retrievedChunks);

        // 10. Update conversation
        conversation.setTotalMessages((int) messageRepository.countByConversation_Id(conversationId));
        conversationRepository.save(conversation);

        log.info("RAG pipeline completed for conversation {}: {} chunks retrieved, {} tokens used",
                conversationId, retrievedChunks.size(), llmResponse.getTotalTokens());

        return ChatAnswerResponse.builder()
                .userMessage(toMessageResponse(userMessage))
                .assistantMessage(toMessageResponse(assistantMessage))
                .citations(citationResponses)
                .build();
    }

    // ───────────────── Private Helpers ─────────────────

    private ChatConversation findConversation(UUID conversationId, UUID userId) {
        // Dùng findByIdAndUser_IdAndActiveTrue để không cho phép truy cập conversation
        // đã bị soft-delete
        return conversationRepository.findByIdAndUser_IdAndActiveTrue(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found or has been deleted"));
    }

    /**
     * Build vector search filter based on conversation's selected documents.
     * Priority: selected document IDs → subject → empty (no global scan).
     */
    private Map<String, Object> buildSearchFilter(ChatConversation conversation, List<UUID> selectedDocIds) {
        Map<String, Object> filter = new LinkedHashMap<>();

        if (selectedDocIds != null && !selectedDocIds.isEmpty()) {
            if (selectedDocIds.size() == 1) {
                filter.put("documentId", selectedDocIds.get(0).toString());
            } else {
                filter.put("documentIds", selectedDocIds.stream().map(UUID::toString).toList());
            }
            return filter;
        }

        if (conversation.getSubject() != null) {
            filter.put("subjectId", conversation.getSubject().getId().toString());
            return filter;
        }

        // No docs attached and no subject: search nothing (never all documents).
        filter.put("documentIds", List.of());
        return filter;
    }

    private List<Document> loadAttachedDocuments(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<Document> docs = documentRepository.findAllById(documentIds);
        Map<UUID, Document> byId = new HashMap<>();
        for (Document doc : docs) {
            byId.put(doc.getId(), doc);
        }
        List<Document> ordered = new ArrayList<>();
        for (UUID id : documentIds) {
            Document doc = byId.get(id);
            if (doc != null) {
                ordered.add(doc);
            }
        }
        return ordered;
    }

    /**
     * Match attached documents whose title appears in the user question
     * (e.g. "Present Require tóm tắt"). Prefers longer title matches.
     */
    private List<Document> matchDocumentsByTitle(String question, List<Document> attachedDocs) {
        if (question == null || question.isBlank() || attachedDocs == null || attachedDocs.isEmpty()) {
            return List.of();
        }
        String normalizedQuestion = normalizeForMatch(question);
        if (normalizedQuestion.isBlank()) {
            return List.of();
        }

        List<Document> ranked = new ArrayList<>(attachedDocs);
        ranked.sort(Comparator.comparingInt((Document d) -> normalizeForMatch(d.getTitle()).length()).reversed());

        List<Document> matched = new ArrayList<>();
        for (Document doc : ranked) {
            String title = normalizeForMatch(doc.getTitle());
            if (title.length() < 3) {
                continue;
            }
            if (normalizedQuestion.contains(title) || fuzzyTitleMatch(normalizedQuestion, title)) {
                matched.add(doc);
            }
        }
        return matched;
    }

    private boolean fuzzyTitleMatch(String normalizedQuestion, String normalizedTitle) {
        // Strip common prefixes like "[r]" so "[R] Present Require" still matches "present require"
        String strippedTitle = normalizedTitle
                .replaceAll("^\\[[^\\]]+]\\s*", "")
                .trim();
        if (strippedTitle.length() >= 3 && normalizedQuestion.contains(strippedTitle)) {
            return true;
        }

        // Require most significant tokens (length >= 3) to appear in the question
        String[] tokens = strippedTitle.split("\\s+");
        int significant = 0;
        int hits = 0;
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            significant++;
            if (normalizedQuestion.contains(token)) {
                hits++;
            }
        }
        return significant > 0 && hits == significant;
    }

    private boolean isSummaryIntent(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = normalizeForMatch(question);
        return q.contains("tom tat")
                || q.contains("summary")
                || q.contains("summarize")
                || q.contains("tong quan")
                || q.contains("noi dung chinh")
                || q.contains("gioi thieu")
                || q.contains("overview");
    }

    /**
     * Decide which docs to load for summary-style questions.
     * - "Present Require tóm tắt" → that file only
     * - "tóm tắt" / title-only mention → matched file, else all attached
     */
    private List<Document> resolveSummaryTargets(
            String question,
            List<Document> titleMatchedDocs,
            List<Document> attachedDocs,
            boolean summaryIntent) {
        if (attachedDocs == null || attachedDocs.isEmpty()) {
            return List.of();
        }
        String normalized = normalizeForMatch(question);
        boolean titleOnly = !titleMatchedDocs.isEmpty() && looksLikeTitleOnlyQuestion(normalized);

        if (!summaryIntent && !titleOnly) {
            return List.of();
        }
        if (!titleMatchedDocs.isEmpty()) {
            return titleMatchedDocs;
        }
        // Explicit summary with no title named → summarize all attached docs
        return summaryIntent ? attachedDocs : List.of();
    }

    /**
     * Short questions that mostly just name a document (e.g. "Present Require")
     * are treated as summary/overview requests.
     */
    private boolean looksLikeTitleOnlyQuestion(String normalizedQuestion) {
        String[] tokens = normalizedQuestion.split("\\s+");
        return tokens.length > 0 && tokens.length <= 6
                && !normalizedQuestion.contains("la gi")
                && !normalizedQuestion.contains("nhu the nao")
                && !normalizedQuestion.contains("bao nhieu")
                && !normalizedQuestion.contains("o dau")
                && !normalizedQuestion.contains("khi nao")
                && !normalizedQuestion.contains("tai sao")
                && !normalizedQuestion.contains("vi sao");
    }

    private String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');
        normalized = normalized.replaceAll("[^a-z0-9\\s\\[\\]]", " ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    /**
     * Load evenly spaced chunks across matched documents (no embedding score).
     * Used for summary / title-only questions where semantic similarity is weak.
     */
    private List<PromptBuilder.RetrievedChunk> loadRepresentativeChunks(List<Document> docs, int maxChunks) {
        if (docs == null || docs.isEmpty() || maxChunks <= 0) {
            return List.of();
        }

        int perDoc = Math.max(1, maxChunks / docs.size());
        List<PromptBuilder.RetrievedChunk> result = new ArrayList<>();
        int citationIndex = 1;

        for (Document doc : docs) {
            List<DocumentChunk> all = chunkRepository.findAllByDocument_IdOrderByChunkIndexAsc(doc.getId());
            if (all.isEmpty()) {
                log.warn("[chat] documentId={} title='{}' has no chunks — cannot summarize",
                        doc.getId(), doc.getTitle());
                continue;
            }
            List<DocumentChunk> sampled = sampleEvenly(all, perDoc);
            for (DocumentChunk chunk : sampled) {
                if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                    continue;
                }
                result.add(new PromptBuilder.RetrievedChunk(
                        citationIndex++,
                        chunk.getId().toString(),
                        doc.getId().toString(),
                        doc.getTitle(),
                        chunk.getContent(),
                        chunk.getPageStart(),
                        chunk.getPageEnd(),
                        1.0));
            }
        }
        return result;
    }

    // Vietnamese stop-words that add noise to lexical matching
    private static final Set<String> STOP_WORDS = Set.of(
            "la", "gi", "cua", "trong", "file", "tai", "lieu", "cho", "va", "co",
            "the", "nao", "bao", "nhieu", "khi", "o", "dau", "vi", "sao", "tai_sao",
            "ban", "minh", "hay", "cac", "mot", "nhung", "voi", "de", "duoc", "thi",
            "a", "an", "the", "of", "in", "is", "are", "what", "when", "where", "how");

    /**
     * Merge semantic chunks with lexical keyword matches from the given docs.
     * Ensures literal terms in the question (e.g. "duration") surface the right chunk
     * even when cross-lingual embedding similarity is low. Deduped by chunkId, re-indexed.
     */
    private List<PromptBuilder.RetrievedChunk> mergeWithLexicalMatches(
            List<PromptBuilder.RetrievedChunk> semanticChunks,
            String question,
            List<Document> scopeDocs,
            int topK) {
        List<String> keywords = extractKeywords(question);
        List<PromptBuilder.RetrievedChunk> lexical = keywords.isEmpty()
                ? List.of()
                : lexicalSearch(keywords, scopeDocs, Math.max(topK, 5));

        if (lexical.isEmpty()) {
            return semanticChunks;
        }

        // Preserve semantic order first, then append new lexical hits.
        LinkedHashMap<String, PromptBuilder.RetrievedChunk> byChunk = new LinkedHashMap<>();
        for (PromptBuilder.RetrievedChunk c : semanticChunks) {
            byChunk.put(c.chunkId(), c);
        }
        for (PromptBuilder.RetrievedChunk c : lexical) {
            byChunk.putIfAbsent(c.chunkId(), c);
        }

        // Re-number citation indices sequentially.
        List<PromptBuilder.RetrievedChunk> merged = new ArrayList<>();
        int idx = 1;
        for (PromptBuilder.RetrievedChunk c : byChunk.values()) {
            merged.add(new PromptBuilder.RetrievedChunk(
                    idx++, c.chunkId(), c.documentId(), c.documentTitle(),
                    c.content(), c.pageStart(), c.pageEnd(), c.score()));
            if (idx > Math.max(topK * 2, 8)) {
                break;
            }
        }
        return merged;
    }

    private List<String> extractKeywords(String question) {
        String normalized = normalizeForMatch(question);
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                keywords.add(token);
            }
        }
        return new ArrayList<>(keywords);
    }

    /**
     * Score chunks of the scoped documents by keyword occurrence and return the best ones.
     */
    private List<PromptBuilder.RetrievedChunk> lexicalSearch(
            List<String> keywords, List<Document> scopeDocs, int limit) {
        if (scopeDocs == null || scopeDocs.isEmpty()) {
            return List.of();
        }

        record Scored(DocumentChunk chunk, Document doc, int score) {
        }
        List<Scored> scored = new ArrayList<>();

        for (Document doc : scopeDocs) {
            List<DocumentChunk> chunks = chunkRepository.findAllByDocument_IdOrderByChunkIndexAsc(doc.getId());
            for (DocumentChunk chunk : chunks) {
                if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                    continue;
                }
                String haystack = normalizeForMatch(chunk.getContent());
                int score = 0;
                for (String kw : keywords) {
                    int from = 0;
                    while ((from = haystack.indexOf(kw, from)) >= 0) {
                        score++;
                        from += kw.length();
                    }
                }
                if (score > 0) {
                    scored.add(new Scored(chunk, doc, score));
                }
            }
        }

        scored.sort(Comparator.comparingInt(Scored::score).reversed());

        List<PromptBuilder.RetrievedChunk> result = new ArrayList<>();
        int idx = 1;
        for (Scored s : scored) {
            result.add(new PromptBuilder.RetrievedChunk(
                    idx++, s.chunk().getId().toString(), s.doc().getId().toString(),
                    s.doc().getTitle(), s.chunk().getContent(),
                    s.chunk().getPageStart(), s.chunk().getPageEnd(), null));
            if (idx > limit) {
                break;
            }
        }
        return result;
    }

    private List<DocumentChunk> sampleEvenly(List<DocumentChunk> chunks, int limit) {
        if (chunks.size() <= limit) {
            return chunks;
        }
        List<DocumentChunk> sampled = new ArrayList<>();
        int n = chunks.size();
        for (int i = 0; i < limit; i++) {
            int idx = (int) Math.round(i * (n - 1) / (double) (limit - 1));
            sampled.add(chunks.get(idx));
        }
        return sampled;
    }

    private List<String> resolveDocumentTitles(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<Document> docs = documentRepository.findAllById(documentIds);
        Map<UUID, String> titleById = new HashMap<>();
        for (Document doc : docs) {
            titleById.put(doc.getId(), doc.getTitle());
        }
        // Preserve conversation selection order
        List<String> titles = new ArrayList<>();
        for (UUID id : documentIds) {
            String title = titleById.get(id);
            if (title != null) {
                titles.add(title);
            }
        }
        return titles;
    }

    /**
     * Ensure the answer always ends with a clear file-level source list.
     * Skips appending if the model already wrote a "Nguồn:" section.
     */
    private String appendSourceFooter(String content, List<PromptBuilder.RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return content;
        }
        String body = content == null ? "" : content.trim();
        if (body.toLowerCase(Locale.ROOT).contains("nguồn:")) {
            return body;
        }

        StringBuilder sb = new StringBuilder(body);
        sb.append("\n\n---\n**Nguồn:**\n");
        for (PromptBuilder.RetrievedChunk chunk : chunks) {
            sb.append("[").append(chunk.citationIndex()).append("] ");
            if (chunk.documentTitle() != null && !chunk.documentTitle().isBlank()) {
                sb.append(chunk.documentTitle());
            } else {
                sb.append("(không rõ tên file)");
            }
            if (chunk.pageStart() != null) {
                sb.append(" (trang ").append(chunk.pageStart());
                if (chunk.pageEnd() != null && !chunk.pageEnd().equals(chunk.pageStart())) {
                    sb.append("-").append(chunk.pageEnd());
                }
                sb.append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Enrich vector search results with document chunk data from MySQL.
     */
    private List<PromptBuilder.RetrievedChunk> enrichSearchResults(List<VectorSearchResult> searchResults) {
        if (searchResults.isEmpty()) {
            return List.of();
        }

        List<PromptBuilder.RetrievedChunk> chunks = new ArrayList<>();
        int citationIndex = 1;

        for (VectorSearchResult result : searchResults) {
            Map<String, Object> metadata = result.getMetadata();
            String chunkIdStr = metadata != null ? (String) metadata.get("chunkId") : null;

            String content = "";
            String documentTitle = "";
            String documentId = "";
            Integer pageStart = null;
            Integer pageEnd = null;

            if (chunkIdStr != null) {
                try {
                    UUID chunkId = UUID.fromString(chunkIdStr);
                    Optional<DocumentChunk> chunkOpt = chunkRepository.findById(chunkId);
                    if (chunkOpt.isPresent()) {
                        DocumentChunk chunk = chunkOpt.get();
                        content = chunk.getContent();
                        pageStart = chunk.getPageStart();
                        pageEnd = chunk.getPageEnd();

                        Document doc = chunk.getDocument();
                        if (doc != null) {
                            documentTitle = doc.getTitle();
                            documentId = doc.getId().toString();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to load chunk {}", chunkIdStr, e);
                }
            }

            // Fallback: use content from metadata if chunk not found in DB
            if (content.isEmpty() && metadata != null && metadata.containsKey("content")) {
                content = (String) metadata.get("content");
            }
            if (documentTitle.isEmpty() && metadata != null && metadata.containsKey("documentTitle")) {
                documentTitle = (String) metadata.get("documentTitle");
            }

            if (!content.isEmpty()) {
                chunks.add(new PromptBuilder.RetrievedChunk(
                        citationIndex++,
                        chunkIdStr != null ? chunkIdStr : result.getId(),
                        documentId,
                        documentTitle,
                        content,
                        pageStart,
                        pageEnd,
                        result.getScore()));
            }
        }

        return chunks;
    }

    /**
     * Save citation records linking the assistant message to the source chunks.
     */
    private List<CitationResponse> saveCitations(ChatMessage assistantMessage,
            List<PromptBuilder.RetrievedChunk> retrievedChunks) {
        List<CitationResponse> responses = new ArrayList<>();

        for (PromptBuilder.RetrievedChunk chunk : retrievedChunks) {
            try {
                DocumentChunk docChunk = null;
                Document document = null;

                if (chunk.chunkId() != null) {
                    docChunk = chunkRepository.findById(UUID.fromString(chunk.chunkId())).orElse(null);
                }
                if (chunk.documentId() != null && !chunk.documentId().isEmpty()) {
                    document = documentRepository.findById(UUID.fromString(chunk.documentId())).orElse(null);
                }

                if (docChunk != null && document != null) {
                    MessageCitation citation = MessageCitation.builder()
                            .message(assistantMessage)
                            .chunk(docChunk)
                            .document(document)
                            .citationIndex(chunk.citationIndex())
                            .score(chunk.score())
                            .pageStart(chunk.pageStart())
                            .pageEnd(chunk.pageEnd())
                            .quotedText(truncate(chunk.content(), 500))
                            .build();
                    citation = citationRepository.save(citation);

                    responses.add(CitationResponse.builder()
                            .id(citation.getId())
                            .citationIndex(chunk.citationIndex())
                            .documentId(document.getId())
                            .documentTitle(document.getTitle())
                            .chunkId(docChunk.getId())
                            .quotedText(citation.getQuotedText())
                            .pageStart(chunk.pageStart())
                            .pageEnd(chunk.pageEnd())
                            .score(chunk.score())
                            .build());
                }
            } catch (Exception e) {
                log.warn("Failed to save citation for chunk {}", chunk.chunkId(), e);
            }
        }

        return responses;
    }

    private ChatAnswerResponse buildErrorResponse(
            ChatConversation conversation,
            ChatMessage userMessage,
            String errorMessage,
            String llmModel) {
        ChatMessage errorMsg = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(errorMessage)
                .llmModel(llmModel)
                .build();
        errorMsg = messageRepository.save(errorMsg);

        return ChatAnswerResponse.builder()
                .userMessage(toMessageResponse(userMessage))
                .assistantMessage(toMessageResponse(errorMsg))
                .citations(List.of())
                .build();
    }

    // ───────────────── Mappers ─────────────────

    private ConversationResponse toConversationResponse(ChatConversation conv) {
        return ConversationResponse.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .subjectId(conv.getSubject() != null ? conv.getSubject().getId() : null)
                .subjectName(conv.getSubject() != null ? conv.getSubject().getName() : null)
                .documentIds(parseDocumentIds(conv.getSelectedDocumentIdsJson()))
                .totalMessages(conv.getTotalMessages())
                .active(conv.getActive())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
    }

    private String serializeDocumentIds(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(documentIds);
        } catch (Exception e) {
            throw new BadRequestException("Invalid document IDs");
        }
    }

    private List<UUID> parseDocumentIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            List<UUID> ids = new ArrayList<>();
            for (String id : raw) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                ids.add(UUID.fromString(id));
            }
            return ids;
        } catch (Exception e) {
            log.warn("Failed to parse selectedDocumentIdsJson", e);
            return List.of();
        }
    }

    private MessageResponse toMessageResponse(ChatMessage msg) {
        return MessageResponse.builder()
                .id(msg.getId())
                .role(msg.getRole().name())
                .content(msg.getContent())
                .llmModel(msg.getLlmModel())
                .promptTokens(msg.getPromptTokens())
                .completionTokens(msg.getCompletionTokens())
                .totalTokens(msg.getTotalTokens())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
