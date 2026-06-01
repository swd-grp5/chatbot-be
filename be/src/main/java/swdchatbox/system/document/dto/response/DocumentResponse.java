package swdchatbox.system.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.enums.DocumentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "subjectId", "subjectCode", "subjectName", "title", "description", "documentType", "status", "totalPages", "totalChunks", "active", "createdAt", "updatedAt", "files"})
public class DocumentResponse {
    private UUID id;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private String title;
    private String description;
    private DocumentType documentType;
    private DocumentStatus status;
    private Integer totalPages;
    private Integer totalChunks;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DocumentFileResponse> files;
}
