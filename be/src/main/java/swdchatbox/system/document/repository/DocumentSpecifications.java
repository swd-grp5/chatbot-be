package swdchatbox.system.document.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.system.document.entity.Document;

import java.time.LocalDateTime;
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

    public static Specification<Document> hasStatus(Enum<?> status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Document> hasActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active);
    }

    public static Specification<Document> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("extractedText")), like),
                    cb.like(cb.lower(root.get("subject").get("name")), like),
                    cb.like(cb.lower(root.get("subject").get("code")), like)
            );
        };
    }

    public static Specification<Document> createdAfter(LocalDateTime createdFrom) {
        return (root, query, cb) -> createdFrom == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
    }

    public static Specification<Document> createdBefore(LocalDateTime createdTo) {
        return (root, query, cb) -> createdTo == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }
}
