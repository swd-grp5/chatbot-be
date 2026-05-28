package swdchatbox.system.document.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import swdchatbox.system.document.enums.DocumentType;

@Getter
@Setter
public class DocumentUpdateRequest {

    @NotNull
    private DocumentType documentType;

    @NotBlank
    private String subjectId;

    @NotBlank
    private String title;

    private String description;

    private Boolean active;
}
