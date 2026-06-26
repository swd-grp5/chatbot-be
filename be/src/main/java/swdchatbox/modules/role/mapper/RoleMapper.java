package swdchatbox.modules.role.mapper;

import swdchatbox.modules.role.dto.response.RoleResponse;
import swdchatbox.modules.role.entity.Role;

public final class RoleMapper {

    private RoleMapper() {
    }

    public static RoleResponse toResponse(Role role) {
        if (role == null) {
            return null;
        }
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .active(role.getActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
