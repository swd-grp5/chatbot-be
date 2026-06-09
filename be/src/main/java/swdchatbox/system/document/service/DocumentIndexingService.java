package swdchatbox.system.document.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentChunk;
import swdchatbox.system.document.entity.DocumentIndexJob;
import swdchatbox.system.document.entity.DocumentIndexJobStatus;
import swdchatbox.system.document.repository.DocumentChunkRepository;
import swdchatbox.system.document.repository.DocumentIndexJobRepository;
import swdchatbox.system.document.repository.DocumentRepository;

import java.util.List;
import java.util.UUID;

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

    @Transactional
    public void processPendingJobs() {
        List<DocumentIndexJob> jobs = documentIndexJobRepository
                .findTop50ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                        List.of(DocumentIndexJobStatus.PENDING, DocumentIndexJobStatus.RETRY),
                        java.time.LocalDateTime.now()
                );

        for (DocumentIndexJob job : jobs) {
            try {
                documentIndexJobService.markProcessing(job);
                index(job.getDocument().getId());
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

        String extractedText = documentExtractionService.extract(document);
        List<DocumentChunk> chunks = documentChunkingService.chunk(document, extractedText);

        documentChunkRepository.deleteAllByDocument_Id(documentId);
        if (!chunks.isEmpty()) {
            documentChunkRepository.saveAll(chunks);
        }

        document.setExtractedText(extractedText);
        document.setTotalChunks(chunks.size());
        document.setTotalPages(documentPageCountService.countDocumentPages(documentId));
        document.setStatus(swdchatbox.system.document.enums.DocumentStatus.INDEXED);
        return documentRepository.save(document);
    }

    public List<DocumentChunk> getChunks(UUID documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return documentChunkRepository.findAllByDocument_IdOrderByChunkIndexAsc(documentId);
    }
}
