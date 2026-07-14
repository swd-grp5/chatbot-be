package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class QuizAttemptResponse {
    private UUID id;
    private UUID quizId;
    private String quizTitle;
    private UUID variantId;
    private Integer variantNumber;
    /** false = giảng viên ẩn điểm; khi đó totalScore/maxScore/percentage/answers sẽ null/rỗng. */
    private Boolean resultsVisible;
    private Double totalScore;
    private Double maxScore;
    private Double percentage;
    private LocalDateTime submittedAt;
    private List<QuizAnswerResultResponse> answers;
}
