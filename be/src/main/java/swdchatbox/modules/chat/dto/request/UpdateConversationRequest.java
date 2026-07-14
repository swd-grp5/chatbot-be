package swdchatbox.modules.chat.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UpdateConversationRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    /**
     * When present, replaces the conversation's selected documents.
     * Pass an empty list to clear the selection (fall back to subject-wide search).
     */
    private List<UUID> documentIds;
}
