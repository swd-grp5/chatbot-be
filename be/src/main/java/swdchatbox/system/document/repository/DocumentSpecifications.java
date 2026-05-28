package swdchatbox.system.document.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.system.document.entity.Document;

import java.util.UUID;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> hasSubjectId(UUID subjectId) {
        return (root, query, cb) -> subjectId == null ? cb.conjunction() : cb.equal(root.get("subject").get("id"), subjectId);
    }

    public static Specification<Document> hasDocumentType(Enum<?> documentType) {
        return (root, query, cb) -> documentType == null ? cb.conjunction() : cb.equal(root.get("documentType"), documentType);
    }

    public static Specification<Document> hasActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active);
    }
}
