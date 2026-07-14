package swdchatbox.modules.subscription.dto.response;

import lombok.*;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserSubscriptionResponse {
    private SubscriptionPlan plan;
    private Integer remainingCredits;
    private LocalDateTime nextResetAt;
}
