package swdchatbox.system.enrollment.repository;

import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import swdchatbox.system.enrollment.entity.UserSubject;
import swdchatbox.system.user.entity.User;

import java.util.UUID;

public final class EnrollmentSpecifications {

    private EnrollmentSpecifications() {
    }

    public static Specification<User> studentEnrolledInSubject(UUID subjectId) {
        return userAssignedToSubject(subjectId);
    }

    public static Specification<User> lecturerEnrolledInSubject(UUID subjectId) {
        return userAssignedToSubject(subjectId);
    }

    public static Specification<User> userAssignedToSubject(UUID subjectId) {
        return (root, query, cb) -> {
            if (subjectId == null) {
                return cb.conjunction();
            }
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var assignmentRoot = subquery.from(UserSubject.class);
            subquery.select(assignmentRoot.get("user").get("id"))
                    .where(cb.equal(assignmentRoot.get("subject").get("id"), subjectId));
            return root.get("id").in(subquery);
        };
    }
}
