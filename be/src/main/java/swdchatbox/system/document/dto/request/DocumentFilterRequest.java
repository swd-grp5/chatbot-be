package swdchatbox.system.document.dto.request;

import lombok.Getter;
import lombok.Setter;
import swdchatbox.system.document.enums.DocumentType;

import java.util.UUID;

@Getter
@Setter
public class DocumentFilterRequest {
    private UUID subjectId;
    private DocumentType documentType;
    private Boolean active;
}
