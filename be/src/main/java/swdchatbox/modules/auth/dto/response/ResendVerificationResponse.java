package swdchatbox.modules.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResendVerificationResponse {

    private boolean success;
    private String message;
}
