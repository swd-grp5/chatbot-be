package swdchatbox.system.subject.mapper;

import swdchatbox.system.subject.dto.response.SubjectResponse;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.user.entity.User;

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
