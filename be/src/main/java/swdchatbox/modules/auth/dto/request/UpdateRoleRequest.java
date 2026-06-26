package swdchatbox.modules.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateRoleRequest {

    @NotNull(message = "roleId is required")
    private UUID roleId;
}
