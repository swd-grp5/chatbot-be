package swdchatbox.system.lecturer.mapper;

import swdchatbox.system.lecturer.dto.response.LecturerResponse;
import swdchatbox.system.subject.dto.response.SubjectSummaryResponse;
import swdchatbox.system.user.entity.User;

import java.util.List;

public final class LecturerMapper {

    private LecturerMapper() {
    }

    public static LecturerResponse toResponse(User user, List<SubjectSummaryResponse> subjects) {
        return LecturerResponse.builder()
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
