package swdchatbox.modules.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.modules.document.entity.DocumentChunk;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findAllByDocument_IdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findAllByIdIn(List<UUID> ids);

    void deleteAllByDocument_Id(UUID documentId);

    long countByDocument_Id(UUID documentId);

    /**
     * Load all chunks that have an embedding stored, for cosine similarity search.
     */
    @Query("SELECT c FROM DocumentChunk c WHERE c.embeddingJson IS NOT NULL AND c.embeddingJson <> ''")
    List<DocumentChunk> findAllWithEmbedding();

    /**
     * Load chunks with embedding, filtered by a set of document IDs.
     */
    @Query("SELECT c FROM DocumentChunk c WHERE c.embeddingJson IS NOT NULL AND c.embeddingJson <> '' AND c.document.id IN :docIds")
    List<DocumentChunk> findAllWithEmbeddingByDocumentIds(@Param("docIds") List<UUID> docIds);

    /**
     * Load chunks with embedding, filtered by a subject ID.
     */
    @Query("SELECT c FROM DocumentChunk c WHERE c.embeddingJson IS NOT NULL AND c.embeddingJson <> '' AND c.document.subject.id = :subjectId")
    List<DocumentChunk> findAllWithEmbeddingBySubjectId(@Param("subjectId") UUID subjectId);
}
