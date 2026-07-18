package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Điểm tùy chỉnh cho một câu hỏi trong pool khi pointsMode = CUSTOM. */
@Getter
@Setter
public class QuestionPointsItem {

    @NotNull(message = "bankQuestionId is required")
    private UUID bankQuestionId;

    @NotNull(message = "points is required")
    @DecimalMin(value = "0.1", message = "points must be at least 0.1")
    private Double points;
}
