package swdchatbox.system.document.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentUpdateRequest {

    @NotBlank
    private String title;

    private String description;

    private Boolean active;
}
