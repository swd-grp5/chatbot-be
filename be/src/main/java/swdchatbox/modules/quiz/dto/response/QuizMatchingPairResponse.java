package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class QuizMatchingPairResponse {
    private UUID id;
    private String leftText;
    private String rightText;
    private Integer sortOrder;
}
