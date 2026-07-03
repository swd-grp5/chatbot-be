package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.quiz.enums.QuizStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class QuizResponse {
    private UUID id;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private UUID createdById;
    private String createdByName;
    private String title;
    private String description;
    private QuizStatus status;
    private Integer timeLimitMinutes;
    private Integer totalPoints;
    private Integer questionCount;
    private Boolean active;
    private Boolean aiGenerated;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<QuizQuestionResponse> questions;
}
