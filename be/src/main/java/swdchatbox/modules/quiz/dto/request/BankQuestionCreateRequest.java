package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BankQuestionCreateRequest {

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

    @NotNull(message = "questionTypeId is required")
    private UUID questionTypeId;

    @NotNull(message = "multipleChoiceMode is required")
    private MultipleChoiceMode multipleChoiceMode;

    @NotBlank(message = "questionText is required")
    private String questionText;

    /** Điểm mặc định gợi ý (optional). */
    @DecimalMin(value = "0.1", message = "defaultPoints must be at least 0.1")
    private Double defaultPoints;

    private UUID sourceDocumentId;

    private String sourceExcerpt;

    @NotEmpty(message = "options are required")
    @Valid
    private List<BankQuestionOptionRequest> options;
}
