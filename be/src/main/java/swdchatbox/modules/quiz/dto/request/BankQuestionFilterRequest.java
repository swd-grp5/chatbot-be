package swdchatbox.modules.quiz.dto.request;

import lombok.Getter;
import lombok.Setter;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;

import java.util.UUID;

@Getter
@Setter
public class BankQuestionFilterRequest {
    private UUID subjectId;
    private UUID questionTypeId;
    private MultipleChoiceMode mode;
    private Boolean active;
    private Boolean aiGenerated;
    private String keyword;
}
