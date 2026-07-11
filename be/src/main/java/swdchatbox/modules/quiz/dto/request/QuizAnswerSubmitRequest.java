package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizAnswerSubmitRequest {

    @NotNull(message = "questionId is required")
    private UUID questionId;

    @NotEmpty(message = "selectedOptionIds are required")
    private List<UUID> selectedOptionIds;
}
