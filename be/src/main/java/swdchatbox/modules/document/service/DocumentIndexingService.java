package swdchatbox.modules.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.ai.service.EmbeddingService;
import swdchatbox.modules.citation.repository.MessageCitationRepository;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.entity.DocumentChunk;
import swdchatbox.modules.document.entity.DocumentIndexJob;
import swdchatbox.modules.document.entity.DocumentIndexJobStatus;
import swdchatbox.modules.document.repository.DocumentChunkRepository;
import swdchatbox.modules.document.repository.DocumentIndexJobRepository;
import swdchatbox.modules.document.repository.DocumentRepository;
import swdchatbox.modules.embedding.repository.EmbeddingRecordRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexingService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentChunkingService documentChunkingService;
    private final DocumentExtractionService documentExtractionService;
    private final DocumentPageCountService documentPageCountService;
    private final DocumentIndexJobRepository documentIndexJobRepository;
    private final DocumentIndexJobService documentIndexJobService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final MessageCitationRepository messageCitationRepository;
    private final EmbeddingRecordRepository embeddingRecordRepository;
    private final EntityManager entityManager;

    // Self reference so the @Transactional boundary on index() is honoured when
    // invoked from processPendingJobs() (a direct self-call would bypass the proxy).
    @Autowired
    @Lazy
    private DocumentIndexingService self;

    /**
     * NOTE: intentionally NOT @Transactional. Each job is claimed and processed in
     * its own committed transaction so a claim is immediately visible to other runs
     * and one job can never be indexed twice (which caused duplicate chunks).
     */
    public void processPendingJobs() {
        List<DocumentIndexJob> jobs = documentIndexJobRepository
                .findTop50ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                        List.of(DocumentIndexJobStatus.PENDING, DocumentIndexJobStatus.RETRY),
                        java.time.LocalDateTime.now());

        for (DocumentIndexJob job : jobs) {
            // Skip jobs already picked up by a concurrent/overlapping run.
            if (!documentIndexJobService.claim(job)) {
                continue;
            }
            try {
                self.index(documentIndexJobService.requireDocumentId(job));
                documentIndexJobService.markCompleted(job);
            } catch (Exception ex) {
                documentIndexJobService.markRetry(job, ex);
            }
        }
    }

    @Transactional
    public Document index(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // 1. Extract text and chunk
        String extractedText = documentExtractionService.extract(document);
        List<DocumentChunk> chunks = documentChunkingService.chunk(document, extractedText);

        // 2. Replace old chunks atomically (citations/embeddings first so FK delete succeeds)
        messageCitationRepository.deleteAllByDocument_Id(documentId);
        embeddingRecordRepository.deleteAllByChunk_Document_Id(documentId);
        documentChunkRepository.deleteAllByDocument_Id(documentId);
        entityManager.flush();

        if (!chunks.isEmpty()) {
            documentChunkRepository.saveAll(chunks);
        }

        // 3. Generate embeddings and store into DocumentChunk.embeddingJson
        if (!chunks.isEmpty()) {
            try {
                List<String> texts = chunks.stream().map(DocumentChunk::getContent).toList();
                List<List<Double>> embeddings = embeddingService.embedBatch(texts);

                for (int i = 0; i < chunks.size(); i++) {
                    if (i < embeddings.size()) {
                        chunks.get(i).setEmbeddingJson(objectMapper.writeValueAsString(embeddings.get(i)));
                    }
                }
                documentChunkRepository.saveAll(chunks);
                log.info("Indexed {} chunks with embeddings for documentId={}", chunks.size(), documentId);
            } catch (Exception e) {
                log.error(
                        "[index] step=embedding.batch documentId={} chunkCount={} error={}",
                        documentId,
                        chunks.size(),
                        e.getMessage(),
                        e);
                throw new RuntimeException("Embedding generation failed during indexing: " + e.getMessage(), e);
            }
        }

        document.setExtractedText(extractedText);
        document.setTotalChunks(chunks.size());
        document.setTotalPages(documentPageCountService.countDocumentPages(documentId));
        document.setStatus(swdchatbox.modules.document.enums.DocumentStatus.INDEXED);
        return documentRepository.save(document);
    }

    public List<DocumentChunk> getChunks(UUID documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return documentChunkRepository.findAllByDocument_IdOrderByChunkIndexAsc(documentId);
    }
}
