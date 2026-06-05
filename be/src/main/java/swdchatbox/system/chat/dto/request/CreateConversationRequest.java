package swdchatbox.system.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateConversationRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private UUID subjectId;

    /**
     * Optional: limit the conversation to specific documents.
     * If empty, all documents in the subject are used.
     */
    private List<UUID> documentIds;
}
