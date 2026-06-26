package swdchatbox.modules.subject.mapper;

import swdchatbox.modules.subject.dto.response.SubjectResponse;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.user.entity.User;

public final class SubjectMapper {

    private SubjectMapper() {
    }

    public static SubjectResponse toResponse(Subject subject, User assignedUser) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .code(subject.getCode())
                .name(subject.getName())
                .description(subject.getDescription())
                .userId(assignedUser != null ? assignedUser.getId() : null)
                .userName(assignedUser != null ? assignedUser.getFullName() : null)
                .active(subject.getActive())
                .createdAt(subject.getCreatedAt())
                .updatedAt(subject.getUpdatedAt())
                .build();
    }
}
