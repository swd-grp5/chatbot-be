package swdchatbox.system.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.document.entity.Subject;

import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
}
