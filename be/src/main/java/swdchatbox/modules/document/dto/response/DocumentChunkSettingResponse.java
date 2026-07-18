package swdchatbox.modules.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "chunkSize", "chunkOverlap", "fromDatabase", "createdAt", "updatedAt"})
public class DocumentChunkSettingResponse {

    private UUID id;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private boolean fromDatabase;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
