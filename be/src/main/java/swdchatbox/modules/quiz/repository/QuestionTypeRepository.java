package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import swdchatbox.modules.quiz.entity.QuestionType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionTypeRepository extends JpaRepository<QuestionType, UUID>, JpaSpecificationExecutor<QuestionType> {

    Optional<QuestionType> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    List<QuestionType> findAllByActiveTrueOrderBySortOrderAscNameAsc();
}
