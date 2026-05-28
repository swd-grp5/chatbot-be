package swdchatbox.system.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendVerificationRequest {

    @Email(message = "Email is invalid")
    @NotBlank(message = "Email is required")
    private String email;
}

