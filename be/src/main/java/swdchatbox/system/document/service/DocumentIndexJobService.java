package swdchatbox.system.document.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.dto.response.DocumentIndexStatusResponse;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentIndexJob;
import swdchatbox.system.document.entity.DocumentIndexJobStatus;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.mapper.DocumentIndexStatusMapper;
import swdchatbox.system.document.repository.DocumentIndexJobRepository;
import swdchatbox.system.document.repository.DocumentRepository;

import java.time.LocalDateTime;
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

    @Transactional
    public void markProcessing(DocumentIndexJob job) {
        job.setStatus(DocumentIndexJobStatus.PROCESSING);
        documentIndexJobRepository.save(job);

        Document document = job.getDocument();
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
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
