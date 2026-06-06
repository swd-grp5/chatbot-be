package swdchatbox.system.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "originalFileName", "mimeType", "fileSize", "createdAt"})
public class DocumentFileResponse {
    private UUID id;
    private String originalFileName;
    private String mimeType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
