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
    private final ObjectMapper objectMapper;

    // ───────────────── Conversation CRUD ─────────────────

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request, User user) {
        Subject subject = null;
        if (request.getSubjectId() != null) {
            subjectEnrollmentService.requireStudentCanAccessSubject(user, request.getSubjectId());
            subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());
        }

        String documentIdsJson = null;
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            try {
                documentIdsJson = objectMapper.writeValueAsString(request.getDocumentIds());
            } catch (Exception e) {
                throw new BadRequestException("Invalid document IDs");
            }
        }

        ChatConversation conversation = ChatConversation.builder()
                .user(user)
                .subject(subject)
                .title(request.getTitle())
                .selectedDocumentIdsJson(documentIdsJson)
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
    public ConversationResponse updateConversationTitle(UUID conversationId, UUID userId,
            UpdateConversationRequest request) {
        ChatConversation conversation = findConversation(conversationId, userId);
        conversation.setTitle(request.getTitle());
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
        ChatConversation conversation = findConversation(conversationId, user.getId());
        String userQuestion = request.getMessage();

        // 1. Save user message
        ChatMessage userMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(userQuestion)
                .build();
        messageRepository.save(userMessage);

        // 2. Embed the user question
        List<Double> queryVector;
        try {
            queryVector = embeddingService.embed(userQuestion);
        } catch (Exception e) {
            log.error("Failed to embed user question", e);
            return buildErrorResponse(conversation, "Không thể xử lý câu hỏi. Vui lòng thử lại.");
        }

        // 3. Vector search — find relevant document chunks
        Map<String, Object> filter = buildSearchFilter(conversation);
        List<VectorSearchResult> searchResults;
        try {
            searchResults = vectorStoreService.search(
                    queryVector,
                    aiProperties.getRetrievalTopK(),
                    filter);
        } catch (Exception e) {
            log.error("Vector search failed", e);
            searchResults = List.of();
        }

        // 4. Enrich search results with chunk data from DB
        List<PromptBuilder.RetrievedChunk> retrievedChunks = enrichSearchResults(searchResults);

        // 5. Load conversation history (lấy trực tiếp theo thứ tự tăng dần, bỏ message
        // vừa lưu)
        List<ChatMessage> allHistory = messageRepository
                .findAllByConversation_IdOrderByCreatedAtAsc(conversationId);
        // Loại bỏ user message vừa lưu (tin nhắn cuối cùng)
        List<ChatMessage> history = allHistory.stream()
                .filter(m -> !m.getId().equals(userMessage.getId()))
                .toList();
        // Giới hạn số tin nhắn lịch sử (conversationHistoryLimit * 2: user + assistant)
        int historyLimit = aiProperties.getConversationHistoryLimit() * 2;
        if (history.size() > historyLimit) {
            history = history.subList(history.size() - historyLimit, history.size());
        }

        // 6. Build prompt
        List<LlmMessage> llmMessages = promptBuilder.buildMessages(
                retrievedChunks,
                history,
                userQuestion,
                aiProperties.getConversationHistoryLimit());

        // 7. Call LLM
        LlmResponse llmResponse;
        try {
            llmResponse = llmService.generate(llmMessages);
        } catch (Exception e) {
            log.error("LLM generation failed", e);
            return buildErrorResponse(conversation, "Không thể tạo câu trả lời. Vui lòng thử lại.");
        }

        // 8. Save assistant message
        ChatMessage assistantMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(llmResponse.getContent())
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
                .message(toMessageResponse(assistantMessage))
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
     * Build Qdrant filter based on conversation's subject and selected documents.
     * Hỗ trợ nhiều document bằng cách dùng danh sách documentIds thay vì chỉ lấy
     * doc đầu tiên.
     */
    private Map<String, Object> buildSearchFilter(ChatConversation conversation) {
        Map<String, Object> filter = new LinkedHashMap<>();

        if (conversation.getSubject() != null) {
            filter.put("subjectId", conversation.getSubject().getId().toString());
        }

        // Nếu conversation có giới hạn tài liệu cụ thể, lọc theo tất cả các tài liệu đó
        if (conversation.getSelectedDocumentIdsJson() != null) {
            try {
                List<String> docIds = objectMapper.readValue(
                        conversation.getSelectedDocumentIdsJson(),
                        new TypeReference<List<String>>() {
                        });
                if (docIds.size() == 1) {
                    // Trường hợp 1 document: dùng filter đơn
                    filter.put("documentId", docIds.get(0));
                } else if (docIds.size() > 1) {
                    // Trường hợp nhiều document: dùng "should" (OR) clause cho Qdrant
                    filter.put("documentIds", docIds);
                }
            } catch (Exception e) {
                log.warn("Failed to parse selectedDocumentIdsJson", e);
            }
        }

        return filter;
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

    private ChatAnswerResponse buildErrorResponse(ChatConversation conversation, String errorMessage) {
        ChatMessage errorMsg = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(errorMessage)
                .build();
        errorMsg = messageRepository.save(errorMsg);

        return ChatAnswerResponse.builder()
                .message(toMessageResponse(errorMsg))
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
                .totalMessages(conv.getTotalMessages())
                .active(conv.getActive())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
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
