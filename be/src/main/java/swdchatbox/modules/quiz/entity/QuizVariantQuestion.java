package swdchatbox.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Một câu hỏi trong một đề, kèm thứ tự hiển thị và thứ tự đáp án đã xáo trộn.
 */
@Entity
@Table(name = "quiz_variant_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizVariantQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private QuizVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    /** Vị trí câu hỏi trong đề. */
    @Column(nullable = false)
    private Integer sortOrder;

    /** Danh sách UUID option theo thứ tự đã xáo trộn, dạng JSON (vd: ["uuid1","uuid2"]). */
    @Column(columnDefinition = "TEXT")
    private String optionOrder;
}
