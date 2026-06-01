package swdchatbox.system.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Full response for a chat message that includes the AI answer and its
 * citations.
 */
@Getter
@Builder
public class ChatAnswerResponse {

    private MessageResponse message;
    private List<CitationResponse> citations;
}
