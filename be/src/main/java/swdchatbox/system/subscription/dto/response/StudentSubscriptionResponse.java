package swdchatbox.system.subscription.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSubscriptionResponse {
    private UUID id;
    private UUID planId;
    private String planName;
    private Integer dailyQuestionLimit;
    private Boolean active;
    private LocalDateTime subscribedAt;
    private LocalDateTime expiresAt; // Thêm ngày hết hạn gói
    private LocalDateTime unsubscribedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}