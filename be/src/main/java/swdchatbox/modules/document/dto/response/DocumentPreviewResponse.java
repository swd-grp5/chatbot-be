package swdchatbox.modules.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.document.enums.DocumentType;
import swdchatbox.modules.document.enums.PreviewContentType;

import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({
        "documentId", "fileName", "documentType", "totalPages",
        "previewContentType", "mimeType", "contentBase64", "textPreview", "truncated"
})
public class DocumentPreviewResponse {
    private UUID documentId;
    private String fileName;
    private DocumentType documentType;
    private Integer totalPages;
    private PreviewContentType previewContentType;
    private String mimeType;
    private String contentBase64;
    private String textPreview;
    private boolean truncated;
}
