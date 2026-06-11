package swdchatbox.system.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.ingestion.entity.IngestionJob;

import java.util.Optional;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    Optional<IngestionJob> findByDocument_Id(UUID documentId);

    void deleteByDocument_Id(UUID documentId);
}
