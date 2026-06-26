package swdchatbox.modules.document.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.modules.document.entity.Document;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> hasSubjectId(UUID subjectId) {
        return (root, query, cb) -> subjectId == null ? cb.conjunction() : cb.equal(root.get("subject").get("id"), subjectId);
    }

    public static Specification<Document> subjectIdIn(Collection<UUID> subjectIds) {
        return (root, query, cb) -> {
            if (subjectIds == null) {
                return cb.conjunction();
            }
            if (subjectIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("subject").get("id").in(subjectIds);
        };
    }

    public static Specification<Document> hasUploadedById(UUID uploadedById) {
        return (root, query, cb) -> uploadedById == null ? cb.conjunction() : cb.equal(root.get("uploadedBy").get("id"), uploadedById);
    }

    public static Specification<Document> uploadedByKeywordLike(String uploadedBy) {
        return (root, query, cb) -> {
            if (uploadedBy == null || uploadedBy.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + uploadedBy.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("uploadedBy").get("fullName")), like),
                    cb.like(cb.lower(root.get("uploadedBy").get("email")), like)
            );
        };
    }

    public static Specification<Document> hasSubjectCode(String subjectCode) {
        return (root, query, cb) -> {
            if (subjectCode == null || subjectCode.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("subject").get("code")), subjectCode.trim().toLowerCase());
        };
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
                    // extractedText is @Lob (CLOB): Hibernate 6 rejects lower() on CLOB.
                    // MySQL utf8mb4 collation is case-insensitive, so plain LIKE is enough.
                    cb.like(root.get("extractedText"), like),
                    cb.like(cb.lower(root.get("subject").get("name")), like),
                    cb.like(cb.lower(root.get("subject").get("code")), like),
                    cb.like(cb.lower(root.get("uploadedBy").get("fullName")), like),
                    cb.like(cb.lower(root.get("uploadedBy").get("email")), like)
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
