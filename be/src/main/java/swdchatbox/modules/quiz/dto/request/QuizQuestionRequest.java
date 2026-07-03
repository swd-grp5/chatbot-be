package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizQuestionRequest {

    @NotNull
    private UUID questionTypeId;

    private MultipleChoiceMode multipleChoiceMode;

    @NotBlank
    private String questionText;

    @NotNull
    private Integer points;

    @NotNull
    private Integer sortOrder;

    /** Tài liệu nguồn của câu hỏi (AI tự điền, hoặc giảng viên giữ lại khi sửa). Optional. */
    private UUID sourceDocumentId;

    /** Đoạn trích tài liệu nguồn để đối chiếu. Optional. */
    private String sourceExcerpt;

    @Valid
    private List<QuizOptionRequest> options;
}
