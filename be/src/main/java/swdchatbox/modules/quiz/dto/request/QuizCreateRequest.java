package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizCreateRequest {

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

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
