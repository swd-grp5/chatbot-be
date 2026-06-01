package swdchatbox.system.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.system.user.dto.response.UserResponse;

@Getter
@Builder
@JsonPropertyOrder({"token", "user", "message"})
public class AuthResponse {

    private String token;
    private UserResponse user;
    private String message;
}