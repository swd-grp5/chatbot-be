package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import swdchatbox.modules.quiz.entity.Quiz;

import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID>, JpaSpecificationExecutor<Quiz> {

    @EntityGraph(attributePaths = {
            "subject", "createdBy", "questions", "questions.questionType",
            "questions.options"
    })
    Optional<Quiz> findWithDetailsById(UUID id);
}
