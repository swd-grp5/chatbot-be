package swdchatbox.modules.role.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RoleFilterRequest {
    private Boolean active;
    private String keyword;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
