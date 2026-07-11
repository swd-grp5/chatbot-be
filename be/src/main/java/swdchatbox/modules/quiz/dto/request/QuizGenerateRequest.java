package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import swdchatbox.modules.quiz.enums.PointsDistributionMode;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizGenerateRequest {

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

    private String title;

    private String description;

    @Min(value = 1, message = "questionCount must be at least 1")
    @Max(value = 20, message = "questionCount must be at most 20")
    private Integer questionCount = 5;


    /** Thang điểm VN: tối đa 10. Mặc định = 10 nếu không truyền. */
    @Min(value = 1, message = "totalPoints must be at least 1")
    @Max(value = 10, message = "totalPoints must be at most 10")
    private Integer totalPoints;

   
    private PointsDistributionMode pointsDistribution = PointsDistributionMode.EVEN;

    @Min(value = 1, message = "timeLimitMinutes must be at least 1")
    @Max(value = 600, message = "timeLimitMinutes must be at most 600")
    private Integer timeLimitMinutes;

    private List<UUID> documentIds;
}
