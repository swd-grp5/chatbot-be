package swdchatbox.system.lecturer.mapper;

import swdchatbox.system.lecturer.dto.response.LecturerResponse;
import swdchatbox.system.user.entity.User;

public final class LecturerMapper {

    private LecturerMapper() {
    }

    public static LecturerResponse toResponse(User user) {
        return LecturerResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .active(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .provider(user.getProvider())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
