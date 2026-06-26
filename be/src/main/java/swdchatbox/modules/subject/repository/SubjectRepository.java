package swdchatbox.modules.subject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import swdchatbox.modules.subject.entity.Subject;

import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID>, JpaSpecificationExecutor<Subject> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    Optional<Subject> findByCode(String code);
}
