package swdchatbox.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Một đề (variant) sinh ra từ pool câu hỏi của quiz: một tập con câu hỏi với thứ tự
 * và thứ tự đáp án đã được xáo trộn cố định.
 */
@Entity
@Table(name = "quiz_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /** Số thứ tự đề: 1, 2, 3... */
    @Column(nullable = false)
    private Integer variantNumber;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<QuizVariantQuestion> questions = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
