package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizOptionRequest {

    @NotBlank
    private String optionText;

    @NotNull
    private Boolean isCorrect;

    @NotNull
    private Integer sortOrder;
}
