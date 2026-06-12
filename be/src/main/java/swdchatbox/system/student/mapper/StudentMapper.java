package swdchatbox.system.student.mapper;

import swdchatbox.system.student.dto.response.StudentResponse;
import swdchatbox.system.user.entity.User;

public final class StudentMapper {

    private StudentMapper() {
    }

    public static StudentResponse toResponse(User user) {
        return StudentResponse.builder()
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
