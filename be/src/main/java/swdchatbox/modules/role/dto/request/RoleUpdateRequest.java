package swdchatbox.modules.role.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleUpdateRequest {

    private String code;

    private String name;

    private String description;

    private Boolean active;
}
