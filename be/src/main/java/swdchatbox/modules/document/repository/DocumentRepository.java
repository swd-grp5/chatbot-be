package swdchatbox.modules.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
 import org.springframework.data.repository.query.Param;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.enums.DocumentStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    long countByStatus(DocumentStatus status);

    @Query("select count(d) from Document d")
    long countAllDocuments();

    boolean existsBySubject_IdAndTitleIgnoreCase(UUID subjectId, String title);

    boolean existsBySubject_IdAndTitleIgnoreCaseAndIdNot(UUID subjectId, String title, UUID id);

    boolean existsByUploadedBy_Id(UUID uploadedById);

    @Query("SELECT d.subject.id, COUNT(d) FROM Document d WHERE d.subject.id IN :subjectIds GROUP BY d.subject.id")
    List<Object[]> countBySubjectIds(@Param("subjectIds") Collection<UUID> subjectIds);
}
