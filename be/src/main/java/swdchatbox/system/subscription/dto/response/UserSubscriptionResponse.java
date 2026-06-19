package swdchatbox.system.subscription.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscriptionResponse {
    private UUID id;
    private UUID planId;
    private String planName;
    private Integer dailyQuestionLimit;
    private Boolean active;
    private LocalDateTime subscribedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime unsubscribedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
