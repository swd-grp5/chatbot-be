package swdchatbox.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import swdchatbox.modules.quiz.enums.QuizStatus;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.user.entity.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizStatus status;

    private Integer timeLimitMinutes;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Boolean aiGenerated = false;

    /** Xáo trộn thứ tự câu hỏi khi sinh đề. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean shuffleQuestions = false;

    /** Xáo trộn thứ tự đáp án khi sinh đề. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean shuffleOptions = false;

    /** Hiển thị điểm/kết quả cho sinh viên sau khi nộp (giống LMS). */
    @Column(nullable = false)
    @Builder.Default
    private Boolean showScore = true;

    /** Số câu mỗi đề rút ra từ pool. Null = dùng toàn bộ pool. */
    private Integer questionsPerVariant;

    /** Số đề (variant) được sinh từ pool. Mặc định 1. */
    @Column(nullable = false)
    @Builder.Default
    private Integer variantCount = 1;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<QuizQuestion> questions = new ArrayList<>();

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("variantNumber ASC")
    @Builder.Default
    private List<QuizVariant> variants = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
