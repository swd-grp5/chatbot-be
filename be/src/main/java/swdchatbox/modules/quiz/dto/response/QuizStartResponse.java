package swdchatbox.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Đề bài sinh viên nhận khi bắt đầu làm quiz: câu hỏi và đáp án đã theo thứ tự (đã xáo trộn nếu có),
 * ẩn đáp án đúng. Gửi kèm variantId khi nộp bài.
 */
@Getter
@Builder
public class QuizStartResponse {
    private UUID quizId;
    private String title;
    private String description;
    private Integer timeLimitMinutes;
    /** Null nếu quiz không chia đề. */
    private UUID variantId;
    private Integer variantNumber;
    private Integer questionCount;
    private Double totalPoints;
    private List<QuizQuestionResponse> questions;
}
