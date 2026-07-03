package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizMatchingPairRequest {

    @NotBlank
    private String leftText;

    @NotBlank
    private String rightText;

    @NotNull
    private Integer sortOrder;
}
