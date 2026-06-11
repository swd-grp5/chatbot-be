package swdchatbox.system.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import swdchatbox.system.ai.config.AiProperties;
import swdchatbox.system.ai.dto.LlmMessage;
import swdchatbox.system.ai.dto.LlmResponse;
import swdchatbox.system.ai.service.EmbeddingService;
import swdchatbox.system.ai.service.LlmService;
import swdchatbox.system.chat.dto.request.CreateConversationRequest;
import swdchatbox.system.chat.dto.request.SendMessageRequest;
import swdchatbox.system.chat.dto.response.*;
import swdchatbox.system.chat.entity.ChatConversation;
import swdchatbox.system.chat.entity.ChatMessage;
import swdchatbox.system.chat.enums.MessageRole;
import swdchatbox.system.chat.repository.ChatConversationRepository;
import swdchatbox.system.chat.repository.ChatMessageRepository;
import swdchatbox.system.citation.entity.MessageCitation;
import swdchatbox.system.citation.repository.MessageCitationRepository;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentChunk;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.document.repository.DocumentChunkRepository;
import swdchatbox.system.document.repository.DocumentRepository;
import swdchatbox.system.subject.repository.SubjectRepository;
import swdchatbox.system.embedding.dto.VectorSearchResult;
import swdchatbox.system.embedding.service.VectorStoreService;
import swdchatbox.system.user.entity.User;

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
            subject = subjectRepository.findById(request.getSubjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
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

        // 5. Load conversation history
        List<ChatMessage> history = messageRepository
                .findTop20ByConversation_IdOrderByCreatedAtDesc(conversationId);
        // Reverse to chronological order, exclude the just-saved user message
        history = new ArrayList<>(history);
        Collections.reverse(history);
        if (!history.isEmpty() && history.get(history.size() - 1).getId().equals(userMessage.getId())) {
            history.remove(history.size() - 1);
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
        return conversationRepository.findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    /**
     * Build Qdrant filter based on conversation's subject and selected documents.
     */
    private Map<String, Object> buildSearchFilter(ChatConversation conversation) {
        Map<String, Object> filter = new LinkedHashMap<>();

        if (conversation.getSubject() != null) {
            filter.put("subjectId", conversation.getSubject().getId().toString());
        }

        // If specific documents are selected, filter by them
        if (conversation.getSelectedDocumentIdsJson() != null) {
            try {
                List<String> docIds = objectMapper.readValue(
                        conversation.getSelectedDocumentIdsJson(),
                        new TypeReference<List<String>>() {
                        });
                if (!docIds.isEmpty()) {
                    // For single doc filter; for multiple, Qdrant needs "should" clause
                    // Simplified: use first document or skip if multiple
                    filter.put("documentId", docIds.get(0));
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
