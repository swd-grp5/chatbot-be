package swdchatbox.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "newPassword is required")
    private String newPassword;
}

