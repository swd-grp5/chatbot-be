package swdchatbox.system.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.system.document.entity.DocumentIndexJob;
import swdchatbox.system.document.entity.DocumentIndexJobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"documentId", "jobId", "status", "retryCount", "maxRetries", "nextRunAt", "lastError", "createdAt", "updatedAt"})
public class DocumentIndexStatusResponse {
    private UUID documentId;
    private UUID jobId;
    private DocumentIndexJobStatus status;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRunAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentIndexStatusResponse from(DocumentIndexJob job) {
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
