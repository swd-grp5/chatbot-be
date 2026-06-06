package swdchatbox.system.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.enums.DocumentStatus;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    long countByStatus(DocumentStatus status);

    @Query("select count(d) from Document d")
    long countAllDocuments();

    boolean existsBySubject_IdAndTitleIgnoreCase(UUID subjectId, String title);

    boolean existsBySubject_IdAndTitleIgnoreCaseAndIdNot(UUID subjectId, String title, UUID id);
}
