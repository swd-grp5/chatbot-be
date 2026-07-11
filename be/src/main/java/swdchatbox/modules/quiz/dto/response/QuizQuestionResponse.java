package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class QuizQuestionResponse {
    private UUID id;
    private QuestionTypeResponse questionType;
    private MultipleChoiceMode multipleChoiceMode;
    private String questionText;
    private Double points;
    private Integer sortOrder;
    /** Chỉ trả về cho giảng viên (khi xem đầy đủ để đối chiếu nguồn). */
    private UUID sourceDocumentId;
    private String sourceDocumentTitle;
    private String sourceExcerpt;
    private List<QuizOptionResponse> options;
}
