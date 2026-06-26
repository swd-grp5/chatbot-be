package swdchatbox.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private String message;
}
