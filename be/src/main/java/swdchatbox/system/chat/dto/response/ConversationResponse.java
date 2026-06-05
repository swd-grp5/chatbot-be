package swdchatbox.system.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ConversationResponse {

    private UUID id;
    private String title;
    private UUID subjectId;
    private String subjectName;
    private Integer totalMessages;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
