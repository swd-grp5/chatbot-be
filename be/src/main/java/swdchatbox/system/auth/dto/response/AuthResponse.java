package swdchatbox.system.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import swdchatbox.system.user.dto.response.UserResponse;

@Getter
@Builder
public class AuthResponse {

    private String token;
    private UserResponse user;
    private String message;
}