package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizOptionRequest {

    @NotBlank(message = "optionText is required")
    private String optionText;

    @NotNull(message = "isCorrect is required")
    private Boolean isCorrect;

    @NotNull(message = "sortOrder is required")
    @Min(value = 0, message = "sortOrder must be >= 0")
    private Integer sortOrder;
}
