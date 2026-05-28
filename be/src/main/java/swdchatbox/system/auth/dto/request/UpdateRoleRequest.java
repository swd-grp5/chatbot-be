package swdchatbox.system.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import swdchatbox.system.user.enums.UserRole;

@Getter
@Setter
public class UpdateRoleRequest {

    @NotNull(message = "role is required")
    private UserRole role;
}

