package swdchatbox.system.document.dto.request;

import lombok.Getter;
import lombok.Setter;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.enums.DocumentType;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class DocumentFilterRequest {
    private UUID subjectId;
    private String subjectCode;
    private UUID uploadedById;
    private String uploadedBy;
    private DocumentType documentType;
    private DocumentStatus status;
    private Boolean active;
    private String keyword;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
