package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QuizSubmitRequest {

    @NotEmpty(message = "answers are required")
    @Valid
    private List<QuizAnswerSubmitRequest> answers;
}
