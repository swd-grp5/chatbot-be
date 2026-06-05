package swdchatbox.system.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.document.entity.DocumentFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, UUID> {
    List<DocumentFile> findAllByDocument_Id(UUID documentId);

    Optional<DocumentFile> findByIdAndDocument_Id(UUID id, UUID documentId);

    void deleteAllByDocument_Id(UUID documentId);

    boolean existsByChecksum(String checksum);
}
