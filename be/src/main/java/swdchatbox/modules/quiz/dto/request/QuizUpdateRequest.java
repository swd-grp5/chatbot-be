package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QuizUpdateRequest {

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    @Min(value = 1, message = "timeLimitMinutes must be at least 1")
    @Max(value = 600, message = "timeLimitMinutes must be at most 600")
    private Integer timeLimitMinutes;

    @NotEmpty(message = "questions are required")
    @Valid
    private List<QuizQuestionRequest> questions;
}
