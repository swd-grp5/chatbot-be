package swdchatbox.system.subscription.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record StudentSubscriptionResponse(
        UUID id,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        Boolean active,
        LocalDateTime subscribedAt,
        LocalDateTime unsubscribedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}


//dong nay de push

