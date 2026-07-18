package swdchatbox.modules.document.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.document.dto.response.DocumentIndexStatusResponse;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.entity.DocumentIndexJob;
import swdchatbox.modules.document.entity.DocumentIndexJobStatus;
import swdchatbox.modules.document.enums.DocumentStatus;
import swdchatbox.modules.document.mapper.DocumentIndexStatusMapper;
import swdchatbox.modules.document.repository.DocumentIndexJobRepository;
import swdchatbox.modules.document.repository.DocumentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIndexJobService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_RETRY_DELAY_SECONDS = 15;

    private final DocumentRepository documentRepository;
    private final DocumentIndexJobRepository documentIndexJobRepository;

    @Transactional
    public DocumentIndexStatusResponse enqueue(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        DocumentIndexJob job = documentIndexJobRepository.findByDocument_Id(documentId)
                .orElseGet(() -> DocumentIndexJob.builder()
                        .document(document)
                        .retryCount(0)
                        .maxRetries(DEFAULT_MAX_RETRIES)
                        .build());

        job.setStatus(DocumentIndexJobStatus.PENDING);
        job.setNextRunAt(LocalDateTime.now());
        job.setLastError(null);
        job = documentIndexJobRepository.save(job);

        document.setStatus(DocumentStatus.UPLOADED);
        documentRepository.save(document);

        return DocumentIndexStatusMapper.toResponse(job);
    }

    @Transactional(readOnly = true)
    public DocumentIndexStatusResponse getStatus(UUID documentId) {
        DocumentIndexJob job = documentIndexJobRepository.findByDocument_Id(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Index job not found"));
        return DocumentIndexStatusMapper.toResponse(job);
    }

    /**
     * Atomically claims the job for processing in its own committed transaction.
     * Returns {@code true} only if this caller won the claim; a job already picked
     * up by another run (status no longer PENDING/RETRY) returns {@code false} so
     * the caller can skip it. This prevents the same document being indexed twice
     * (which produced duplicate chunks).
     */
    @Transactional
    public boolean claim(DocumentIndexJob job) {
        int updated = documentIndexJobRepository.claimForProcessing(
                job.getId(),
                DocumentIndexJobStatus.PROCESSING,
                List.of(DocumentIndexJobStatus.PENDING, DocumentIndexJobStatus.RETRY));
        if (updated == 0) {
            return false;
        }
        job.setStatus(DocumentIndexJobStatus.PROCESSING);

        Document document = job.getDocument();
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        return true;
    }

    @Transactional
    public void markCompleted(DocumentIndexJob job) {
        job.setStatus(DocumentIndexJobStatus.COMPLETED);
        job.setLastError(null);
        job.setNextRunAt(null);
        documentIndexJobRepository.save(job);

        Document document = job.getDocument();
        document.setStatus(DocumentStatus.INDEXED);
        documentRepository.save(document);
    }

    @Transactional
    public void markRetry(DocumentIndexJob job, Exception ex) {
        int nextRetryCount = job.getRetryCount() + 1;
        if (nextRetryCount > job.getMaxRetries()) {
            job.setStatus(DocumentIndexJobStatus.FAILED);
            job.setLastError(ex.getMessage());
            job.setNextRunAt(null);
            documentIndexJobRepository.save(job);

            Document document = job.getDocument();
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            return;
        }

        job.setRetryCount(nextRetryCount);
        job.setStatus(DocumentIndexJobStatus.RETRY);
        job.setLastError(ex.getMessage());
        job.setNextRunAt(LocalDateTime.now().plusSeconds(DEFAULT_RETRY_DELAY_SECONDS));
        documentIndexJobRepository.save(job);

        Document document = job.getDocument();
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
    }

    @Transactional
    public void validateCanEnqueue(UUID documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }
}
