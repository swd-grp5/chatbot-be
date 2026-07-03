package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionTypeRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;
    private Integer sortOrder;
    private Boolean active;
}
