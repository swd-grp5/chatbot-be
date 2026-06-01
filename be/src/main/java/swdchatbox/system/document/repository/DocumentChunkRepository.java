package swdchatbox.system.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.document.entity.DocumentChunk;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findAllByDocument_IdOrderByChunkIndexAsc(UUID documentId);

    void deleteAllByDocument_Id(UUID documentId);
}
