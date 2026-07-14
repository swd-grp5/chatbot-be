package swdchatbox.modules.quiz.mapper;

import swdchatbox.modules.quiz.dto.response.BankQuestionOptionResponse;
import swdchatbox.modules.quiz.dto.response.BankQuestionResponse;
import swdchatbox.modules.quiz.entity.BankQuestion;

public final class BankQuestionMapper {

    private BankQuestionMapper() {
    }

    public static BankQuestionResponse toResponse(BankQuestion q) {
        return BankQuestionResponse.builder()
                .id(q.getId())
                .subjectId(q.getSubject() != null ? q.getSubject().getId() : null)
                .subjectCode(q.getSubject() != null ? q.getSubject().getCode() : null)
                .subjectName(q.getSubject() != null ? q.getSubject().getName() : null)
                .createdById(q.getCreatedBy() != null ? q.getCreatedBy().getId() : null)
                .createdByName(q.getCreatedBy() != null ? q.getCreatedBy().getFullName() : null)
                .questionType(QuestionTypeMapper.toResponse(q.getQuestionType()))
                .multipleChoiceMode(q.getMultipleChoiceMode())
                .questionText(q.getQuestionText())
                .defaultPoints(q.getDefaultPoints())
                .sourceDocumentId(q.getSourceDocument() != null ? q.getSourceDocument().getId() : null)
                .sourceDocumentTitle(q.getSourceDocument() != null ? q.getSourceDocument().getTitle() : null)
                .sourceExcerpt(q.getSourceExcerpt())
                .aiGenerated(q.getAiGenerated())
                .active(q.getActive())
                .options(q.getOptions().stream()
                        .map(o -> BankQuestionOptionResponse.builder()
                                .id(o.getId())
                                .optionText(o.getOptionText())
                                .isCorrect(o.getIsCorrect())
                                .sortOrder(o.getSortOrder())
                                .build())
                        .toList())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }
}
