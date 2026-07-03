package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.quiz.entity.QuizAttempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    @EntityGraph(attributePaths = {"quiz", "answers", "answers.question", "answers.question.questionType"})
    List<QuizAttempt> findAllByQuiz_IdAndStudent_IdOrderBySubmittedAtDesc(UUID quizId, UUID studentId);

    @EntityGraph(attributePaths = {"quiz", "answers", "answers.question", "answers.question.questionType"})
    Optional<QuizAttempt> findByIdAndStudent_Id(UUID attemptId, UUID studentId);
}
