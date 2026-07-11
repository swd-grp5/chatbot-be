package swdchatbox.system.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({
        "id", "menuGroupId", "title", "url", "icon", "description",
        "displayOrder", "active", "requiredRole", "createdAt", "updatedAt"
})
public class MenuItemResponse {
    private UUID id;
    private UUID menuGroupId;
    private String title;
    private String url;
    private String icon;
    private String description;
    private Integer displayOrder;
    private Boolean active;
    private String requiredRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
