package swdchatbox.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.user.dto.response.UserResponse;

@Getter
@Builder
@JsonPropertyOrder({"token", "user", "message"})
public class AuthResponse {

    private String token;
    private UserResponse user;
    private String message;
}