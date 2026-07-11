package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class QuizAnswerResultResponse {
    private UUID questionId;
    private QuestionTypeResponse questionType;
    private String questionText;
    private Boolean isCorrect;
    private Double scoreEarned;
    private Double maxScore;
}
