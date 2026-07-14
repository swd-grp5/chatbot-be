package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.quiz.entity.QuizVariant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizVariantRepository extends JpaRepository<QuizVariant, UUID> {

    @EntityGraph(attributePaths = {
            "quiz", "questions", "questions.question", "questions.question.questionType",
            "questions.question.options"
    })
    Optional<QuizVariant> findWithDetailsById(UUID id);

    List<QuizVariant> findAllByQuiz_IdOrderByVariantNumberAsc(UUID quizId);
}
