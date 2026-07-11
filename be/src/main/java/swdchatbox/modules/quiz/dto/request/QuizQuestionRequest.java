package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class QuizQuestionRequest {

    @NotNull(message = "questionTypeId is required")
    private UUID questionTypeId;

    @NotNull(message = "multipleChoiceMode is required")
    private MultipleChoiceMode multipleChoiceMode;

    @NotBlank(message = "questionText is required")
    private String questionText;

    @NotNull(message = "points is required")
    @DecimalMin(value = "0.1", message = "points must be at least 0.1")
    private Double points;

    @NotNull(message = "sortOrder is required")
    @Min(value = 0, message = "sortOrder must be >= 0")
    private Integer sortOrder;

    /** Tài liệu nguồn của câu hỏi (AI tự điền, hoặc giảng viên giữ lại khi sửa). Optional. */
    private UUID sourceDocumentId;

    /** Đoạn trích tài liệu nguồn để đối chiếu. Optional. */
    private String sourceExcerpt;

    @NotEmpty(message = "options are required")
    @Valid
    private List<QuizOptionRequest> options;
}
