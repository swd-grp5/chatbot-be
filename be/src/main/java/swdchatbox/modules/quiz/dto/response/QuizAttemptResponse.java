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
    private Integer totalScore;
    private Integer maxScore;
    private Double percentage;
    private LocalDateTime submittedAt;
    private List<QuizAnswerResultResponse> answers;
}
