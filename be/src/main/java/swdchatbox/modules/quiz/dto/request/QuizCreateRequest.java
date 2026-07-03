package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
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

    @NotNull
    private UUID subjectId;

    @NotBlank
    private String title;

    private String description;
    private Integer timeLimitMinutes;

    @NotEmpty
    @Valid
    private List<QuizQuestionRequest> questions;
}
