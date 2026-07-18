package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class BankQuestionOptionResponse {
    private UUID id;
    private String optionText;
    private Boolean isCorrect;
    private Integer sortOrder;
}
