package swdchatbox.system.subject.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.system.subject.entity.Subject;

public final class SubjectSpecifications {

    private SubjectSpecifications() {
    }

    public static Specification<Subject> hasActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active);
    }

    public static Specification<Subject> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like)
            );
        };
    }
}
