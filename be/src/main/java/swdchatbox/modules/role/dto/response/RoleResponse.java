package swdchatbox.modules.role.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "code", "name", "description", "active", "createdAt", "updatedAt"})
public class RoleResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
