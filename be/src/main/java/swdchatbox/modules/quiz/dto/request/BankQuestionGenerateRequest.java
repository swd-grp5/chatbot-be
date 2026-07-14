package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * AI sinh câu hỏi trực tiếp vào ngân hàng câu hỏi (không tạo quiz).
 */
@Getter
@Setter
public class BankQuestionGenerateRequest {

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

    @Min(value = 1, message = "questionCount must be at least 1")
    @Max(value = 20, message = "questionCount must be at most 20")
    private Integer questionCount = 5;

    /** Điểm mặc định gán cho mọi câu sinh ra (optional). Null = giữ điểm gợi ý do AI chia. */
    @DecimalMin(value = "0.1", message = "defaultPoints must be at least 0.1")
    private Double defaultPoints;

    /** Giới hạn tài liệu nguồn (optional). */
    private List<UUID> documentIds;
}
