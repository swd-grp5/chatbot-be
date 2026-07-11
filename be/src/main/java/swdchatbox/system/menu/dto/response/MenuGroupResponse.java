package swdchatbox.system.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({
        "id", "name", "description", "active", "displayOrder",
        "items", "createdAt", "updatedAt"
})
public class MenuGroupResponse {
    private UUID id;
    private String name;
    private String description;
    private Boolean active;
    private Integer displayOrder;
    private List<MenuItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
