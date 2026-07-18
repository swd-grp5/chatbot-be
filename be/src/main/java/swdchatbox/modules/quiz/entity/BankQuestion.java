package swdchatbox.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.user.entity.User;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Câu hỏi trong ngân hàng câu hỏi (tái sử dụng cho nhiều quiz). Mỗi câu thuộc một môn học.
 */
@Entity
@Table(name = "bank_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_type_id", nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MultipleChoiceMode multipleChoiceMode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** Điểm mặc định gợi ý; quiz có thể ghi đè khi chia điểm. */
    private Double defaultPoints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Document sourceDocument;

    @Column(columnDefinition = "TEXT")
    private String sourceExcerpt;

    /** true = do AI sinh ra, false = giảng viên soạn tay. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean aiGenerated = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "bankQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private Set<BankQuestionOption> options = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
