package swdchatbox.modules.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ConversationResponse {

    private UUID id;
    private String title;
    private UUID subjectId;
    private String subjectName;
    /** Documents scoped to this conversation; reused for every message. */
    private List<UUID> documentIds;
    private Integer totalMessages;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
