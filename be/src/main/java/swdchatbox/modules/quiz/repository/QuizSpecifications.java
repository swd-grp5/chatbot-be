package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.modules.quiz.entity.Quiz;
import swdchatbox.modules.quiz.enums.QuizStatus;

import java.util.Collection;
import java.util.UUID;

public final class QuizSpecifications {

    private QuizSpecifications() {
    }

    public static Specification<Quiz> hasSubjectId(UUID subjectId) {
        return (root, query, cb) -> subjectId == null ? cb.conjunction() : cb.equal(root.get("subject").get("id"), subjectId);
    }

    public static Specification<Quiz> subjectIdIn(Collection<UUID> subjectIds) {
        return (root, query, cb) -> {
            if (subjectIds == null) return cb.conjunction();
            if (subjectIds.isEmpty()) return cb.disjunction();
            return root.get("subject").get("id").in(subjectIds);
        };
    }

    public static Specification<Quiz> hasStatus(QuizStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Quiz> hasActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active);
    }

    public static Specification<Quiz> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return cb.conjunction();
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)
            );
        };
    }
}
