package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import swdchatbox.modules.quiz.enums.PointsDistributionMode;

import java.util.List;
import java.util.UUID;

/**
 * Tạo một quiz mới bằng cách chọn câu hỏi từ ngân hàng câu hỏi.
 * Hỗ trợ chia điểm (đều/tự chia), ẩn-hiện điểm, xáo trộn, và chia N đề.
 */
@Getter
@Setter
public class QuizAssembleRequest {

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    @Min(value = 1, message = "timeLimitMinutes must be at least 1")
    @Max(value = 600, message = "timeLimitMinutes must be at most 600")
    private Integer timeLimitMinutes;

    /** Pool câu hỏi lấy từ ngân hàng. */
    @NotEmpty(message = "bankQuestionIds are required")
    private List<UUID> bankQuestionIds;

    /** Số câu mỗi đề. Null hoặc >= pool size => dùng toàn bộ pool. */
    @Min(value = 1, message = "questionsPerVariant must be at least 1")
    private Integer questionsPerVariant;

    /** Số đề sinh ra. Mặc định 1. */
    @Min(value = 1, message = "variantCount must be at least 1")
    @Max(value = 50, message = "variantCount must be at most 50")
    private Integer variantCount = 1;

    private Boolean shuffleQuestions = false;

    private Boolean shuffleOptions = false;

    /** true = sinh viên thấy điểm sau khi nộp. */
    private Boolean showScore = true;

    /** true = sinh viên có thể làm lại sau khi đã nộp. */
    private Boolean allowRetake = false;

    /** EVEN = chia đều totalPoints; CUSTOM = dùng customPoints từng câu. */
    private PointsDistributionMode pointsMode = PointsDistributionMode.EVEN;

    /** Tổng điểm khi chia đều (mặc định 10). */
    @DecimalMin(value = "0.1", message = "totalPoints must be at least 0.1")
    private Double totalPoints;

    /** Điểm từng câu khi pointsMode = CUSTOM. */
    @Valid
    private List<QuestionPointsItem> customPoints;
}
