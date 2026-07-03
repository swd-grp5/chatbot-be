package swdchatbox.modules.quiz.dto.request;

import lombok.Getter;
import lombok.Setter;
import swdchatbox.modules.quiz.enums.QuizStatus;

import java.util.UUID;

@Getter
@Setter
public class QuizFilterRequest {
    private UUID subjectId;
    private QuizStatus status;
    private Boolean active;
    private String keyword;
}
