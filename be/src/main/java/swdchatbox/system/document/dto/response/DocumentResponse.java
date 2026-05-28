package swdchatbox.system.document.dto.response;

import lombok.Builder;
import lombok.Getter;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.enums.DocumentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
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
    private String extractedText;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DocumentFileResponse> files;

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .subjectId(document.getSubject() != null ? document.getSubject().getId() : null)
                .subjectCode(document.getSubject() != null ? document.getSubject().getCode() : null)
                .subjectName(document.getSubject() != null ? document.getSubject().getName() : null)
                .title(document.getTitle())
                .description(document.getDescription())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .totalPages(document.getTotalPages())
                .totalChunks(document.getTotalChunks())
                .extractedText(document.getExtractedText())
                .active(document.getActive())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
