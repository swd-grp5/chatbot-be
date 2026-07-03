package swdchatbox.modules.quiz.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizGenerateRequest {

    @NotNull
    private UUID subjectId;

    private String title;

    private String description;

    @Min(1)
    @Max(20)
    private Integer questionCount = 5;

    private Integer timeLimitMinutes;

    /** Optional: chỉ dùng nội dung từ các tài liệu này. Bỏ trống = toàn bộ tài liệu môn học. */
    private List<UUID> documentIds;
}
