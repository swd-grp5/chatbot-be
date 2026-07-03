package swdchatbox.modules.quiz.mapper;

import swdchatbox.modules.quiz.dto.response.QuestionTypeResponse;
import swdchatbox.modules.quiz.entity.QuestionType;

public final class QuestionTypeMapper {

    private QuestionTypeMapper() {
    }

    public static QuestionTypeResponse toResponse(QuestionType questionType) {
        if (questionType == null) return null;
        return QuestionTypeResponse.builder()
                .id(questionType.getId())
                .code(questionType.getCode())
                .name(questionType.getName())
                .description(questionType.getDescription())
                .sortOrder(questionType.getSortOrder())
                .active(questionType.getActive())
                .createdAt(questionType.getCreatedAt())
                .updatedAt(questionType.getUpdatedAt())
                .build();
    }
}
