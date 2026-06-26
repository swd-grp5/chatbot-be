package swdchatbox.modules.user.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.modules.user.entity.User;

import java.time.LocalDateTime;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRoleCode(String roleCode) {
        return (root, query, cb) -> roleCode == null ? cb.conjunction() : cb.equal(root.get("role").get("code"), roleCode);
    }

    public static Specification<User> hasActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("isActive"), active);
    }

    public static Specification<User> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like)
            );
        };
    }

    public static Specification<User> createdAfter(LocalDateTime createdFrom) {
        return (root, query, cb) -> createdFrom == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
    }

    public static Specification<User> createdBefore(LocalDateTime createdTo) {
        return (root, query, cb) -> createdTo == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }
}
