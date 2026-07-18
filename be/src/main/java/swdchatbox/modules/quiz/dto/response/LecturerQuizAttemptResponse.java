package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class LecturerQuizAttemptResponse {
    private UUID id;
    private UUID quizId;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private UUID variantId;
    private Integer variantNumber;
    private Double totalScore;
    private Double maxScore;
    private Double percentage;
    private LocalDateTime submittedAt;
}
