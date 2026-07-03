package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.quiz.entity.QuizQuestion;

import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    long countByQuestionType_Id(UUID questionTypeId);
}
