package swdchatbox.system.subject.mapper;

import swdchatbox.system.subject.dto.response.SubjectResponse;
import swdchatbox.system.subject.entity.Subject;

public final class SubjectMapper {

    private SubjectMapper() {
    }

    public static SubjectResponse toResponse(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .code(subject.getCode())
                .name(subject.getName())
                .description(subject.getDescription())
                .active(subject.getActive())
                .createdAt(subject.getCreatedAt())
                .updatedAt(subject.getUpdatedAt())
                .build();
    }
}
