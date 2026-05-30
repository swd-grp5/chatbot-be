package swdchatbox.system.document.mapper;

import swdchatbox.system.document.dto.response.DocumentIndexStatusResponse;
import swdchatbox.system.document.entity.DocumentIndexJob;

public final class DocumentIndexStatusMapper {

    private DocumentIndexStatusMapper() {
    }

    public static DocumentIndexStatusResponse toResponse(DocumentIndexJob job) {
        return DocumentIndexStatusResponse.builder()
                .documentId(job.getDocument() != null ? job.getDocument().getId() : null)
                .jobId(job.getId())
                .status(job.getStatus())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .nextRunAt(job.getNextRunAt())
                .lastError(job.getLastError())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
