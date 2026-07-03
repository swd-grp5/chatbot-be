package swdchatbox.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_type_id", nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MultipleChoiceMode multipleChoiceMode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false)
    private Integer sortOrder;

    /** Tài liệu nguồn AI đã dựa vào để sinh câu hỏi (dùng để giảng viên đối chiếu). Có thể null với câu tạo tay. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Document sourceDocument;

    /** Đoạn trích từ tài liệu nguồn để đối chiếu đáp án đúng. */
    @Column(columnDefinition = "TEXT")
    private String sourceExcerpt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<QuizOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<QuizMatchingPair> matchingPairs = new ArrayList<>();
}
