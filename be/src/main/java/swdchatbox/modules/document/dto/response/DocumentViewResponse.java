package swdchatbox.modules.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.document.enums.DocumentType;

import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "title", "description", "documentType", "totalPages", "fileId", "fileName", "mimeType"})
public class DocumentViewResponse {
    private UUID id;
    private String title;
    private String description;
    private DocumentType documentType;
    private Integer totalPages;
    private UUID fileId;
    private String fileName;
    private String mimeType;
}
