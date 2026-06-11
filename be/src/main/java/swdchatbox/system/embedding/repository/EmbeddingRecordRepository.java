package swdchatbox.system.embedding.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.embedding.entity.EmbeddingRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmbeddingRecordRepository extends JpaRepository<EmbeddingRecord, UUID> {

    Optional<EmbeddingRecord> findByChunk_Id(UUID chunkId);

    Optional<EmbeddingRecord> findByVectorId(String vectorId);

    List<EmbeddingRecord> findAllByChunk_IdIn(List<UUID> chunkIds);

    List<EmbeddingRecord> findAllByCollection_Id(UUID collectionId);

    List<EmbeddingRecord> findAllByChunk_Document_Id(UUID documentId);

    void deleteAllByChunk_Document_Id(UUID documentId);
}
