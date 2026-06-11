package swdchatbox.system.user.mapper;

import swdchatbox.system.role.mapper.RoleMapper;
import swdchatbox.system.user.dto.response.UserResponse;
import swdchatbox.system.user.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(RoleMapper.toResponse(user.getRole()))
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
