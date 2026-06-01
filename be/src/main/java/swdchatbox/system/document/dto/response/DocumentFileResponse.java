package swdchatbox.system.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.system.document.entity.DocumentFile;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "originalFileName", "storedFileName", "filePath", "mimeType", "fileSize", "checksum", "createdAt"})
public class DocumentFileResponse {
    private UUID id;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private String mimeType;
    private Long fileSize;
    private String checksum;
    private LocalDateTime createdAt;

    public static DocumentFileResponse from(DocumentFile file) {
        return DocumentFileResponse.builder()
                .id(file.getId())
                .originalFileName(file.getOriginalFileName())
                .storedFileName(file.getStoredFileName())
                .filePath(file.getFilePath())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .checksum(file.getChecksum())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
