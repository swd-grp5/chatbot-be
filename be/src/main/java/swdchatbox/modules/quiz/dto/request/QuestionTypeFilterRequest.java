package swdchatbox.modules.quiz.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionTypeFilterRequest {
    private Boolean active;
    private String keyword;
}
