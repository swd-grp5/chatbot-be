package swdchatbox.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import swdchatbox.modules.role.dto.response.RoleResponse;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@JsonPropertyOrder({"id", "fullName", "email", "role", "isActive", "createdAt", "updatedAt"})
public record UserResponse(
        UUID id,
        String fullName,
        String email,
        RoleResponse role,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
