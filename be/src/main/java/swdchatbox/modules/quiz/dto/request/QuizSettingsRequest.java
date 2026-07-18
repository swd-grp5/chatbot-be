package swdchatbox.modules.quiz.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizSettingsRequest {

    /** true = sinh viên thấy điểm sau khi nộp. */
    private Boolean showScore;

    /** true = sinh viên có thể làm lại sau khi đã nộp. */
    private Boolean allowRetake;
}
