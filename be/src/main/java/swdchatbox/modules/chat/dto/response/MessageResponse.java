package swdchatbox.modules.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A single chat message.
 * {@code role} is {@code USER} (người dùng), {@code ASSISTANT} (bot/AI), or {@code SYSTEM}.
 */
@Getter
@Builder
public class MessageResponse {

    private UUID id;
    /** {@code USER} | {@code ASSISTANT} | {@code SYSTEM} */
    private String role;
    private String content;
    private String llmModel;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
    /** Present for ASSISTANT messages that have source citations. */
    private List<CitationResponse> citations;
}
