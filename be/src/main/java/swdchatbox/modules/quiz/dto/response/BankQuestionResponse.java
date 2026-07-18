package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BankQuestionResponse {
    private UUID id;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private UUID createdById;
    private String createdByName;
    private QuestionTypeResponse questionType;
    private MultipleChoiceMode multipleChoiceMode;
    private String questionText;
    private Double defaultPoints;
    private UUID sourceDocumentId;
    private String sourceDocumentTitle;
    private String sourceExcerpt;
    private Boolean aiGenerated;
    private Boolean active;
    private List<BankQuestionOptionResponse> options;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
