package swdchatbox.system.subject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.subject.entity.Subject;

import java.util.UUID;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    boolean existsByCode(String code);

    Optional<Subject> findByCode(String code);
}
