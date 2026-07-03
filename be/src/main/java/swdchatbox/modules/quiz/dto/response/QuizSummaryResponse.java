package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.quiz.enums.QuizStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class QuizSummaryResponse {
    private UUID id;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private String title;
    private QuizStatus status;
    private Integer timeLimitMinutes;
    private Integer totalPoints;
    private Integer questionCount;
    private Boolean active;
    private Boolean aiGenerated;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
