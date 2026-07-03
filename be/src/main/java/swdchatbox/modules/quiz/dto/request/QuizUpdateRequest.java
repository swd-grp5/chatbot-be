package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QuizUpdateRequest {

    @NotBlank
    private String title;

    private String description;
    private Integer timeLimitMinutes;

    @NotEmpty
    @Valid
    private List<QuizQuestionRequest> questions;
}
