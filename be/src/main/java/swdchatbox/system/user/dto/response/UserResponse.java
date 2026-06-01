package swdchatbox.system.user.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import swdchatbox.system.user.enums.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@JsonPropertyOrder({"id", "fullName", "email", "role", "isActive", "createdAt", "updatedAt"})
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