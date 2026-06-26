package swdchatbox.modules.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.document.entity.DocumentIndexJob;
import swdchatbox.modules.document.entity.DocumentIndexJobStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentIndexJobRepository extends JpaRepository<DocumentIndexJob, UUID> {
    Optional<DocumentIndexJob> findByDocument_Id(UUID documentId);

    void deleteAllByDocument_Id(UUID documentId);

    List<DocumentIndexJob> findTop50ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            List<DocumentIndexJobStatus> statuses,
            LocalDateTime now
    );
}
