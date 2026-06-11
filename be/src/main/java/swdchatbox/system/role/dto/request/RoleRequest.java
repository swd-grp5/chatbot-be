package swdchatbox.system.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;

    private Boolean active;
}
