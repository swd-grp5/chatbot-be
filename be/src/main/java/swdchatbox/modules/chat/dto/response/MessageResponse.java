package swdchatbox.modules.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MessageResponse {

    private UUID id;
    private String role;
    private String content;
    private String llmModel;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
}
