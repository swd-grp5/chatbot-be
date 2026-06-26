package swdchatbox.modules.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.document.entity.DocumentChunk;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findAllByDocument_IdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findAllByIdIn(List<UUID> ids);

    void deleteAllByDocument_Id(UUID documentId);

    long countByDocument_Id(UUID documentId);
}
