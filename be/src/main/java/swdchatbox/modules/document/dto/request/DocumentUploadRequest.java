package swdchatbox.modules.document.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class DocumentUploadRequest {

    private UUID subjectId;

    private String title;

    private String description;
}
