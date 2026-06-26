package swdchatbox.modules.student.mapper;

import swdchatbox.modules.student.dto.response.StudentResponse;
import swdchatbox.modules.subject.dto.response.SubjectSummaryResponse;
import swdchatbox.modules.user.entity.User;

import java.util.List;

public final class StudentMapper {

    private StudentMapper() {
    }

    public static StudentResponse toResponse(User user, List<SubjectSummaryResponse> subjects) {
        return StudentResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .subjects(subjects)
                .active(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .provider(user.getProvider())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
