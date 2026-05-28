package swdchatbox.system.user.dto.response;

import lombok.Builder;
import swdchatbox.system.user.enums.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserResponse(
        UUID id,
        String fullName,
        String email,
        UserRole role,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}