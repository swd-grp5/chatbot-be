package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class QuizVariantSummaryResponse {
    private UUID id;
    private Integer variantNumber;
    private Integer questionCount;
    private Double totalPoints;
}
