package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizSubmitRequest {

    /** Bắt buộc nếu quiz có chia đề: đề (variant) mà sinh viên đã làm. */
    private UUID variantId;

    @NotEmpty(message = "answers are required")
    @Valid
    private List<QuizAnswerSubmitRequest> answers;
}
