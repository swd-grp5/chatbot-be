package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import swdchatbox.modules.quiz.entity.BankQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankQuestionRepository
        extends JpaRepository<BankQuestion, UUID>, JpaSpecificationExecutor<BankQuestion> {

    @Override
    @EntityGraph(attributePaths = {"subject", "createdBy", "questionType", "options"})
    Optional<BankQuestion> findById(UUID id);

    @EntityGraph(attributePaths = {"subject", "createdBy", "questionType", "options"})
    List<BankQuestion> findAllByIdIn(List<UUID> ids);

    long countByQuestionType_Id(UUID questionTypeId);
}
