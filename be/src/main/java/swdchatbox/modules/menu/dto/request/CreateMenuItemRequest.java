package swdchatbox.modules.menu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateMenuItemRequest {

    @NotNull
    private UUID menuGroupId;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String url;

    @Size(max = 50)
    private String icon;

    @Size(max = 500)
    private String description;

    private Integer displayOrder;

    private Boolean active;

    @Size(max = 50)
    private String requiredRole;
}
