package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizAnswerSubmitRequest {

    @NotNull
    private UUID questionId;

    private List<UUID> selectedOptionIds;
}
