package swdchatbox.system.enrollment.repository;

import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import swdchatbox.system.enrollment.entity.LecturerSubject;
import swdchatbox.system.enrollment.entity.StudentSubject;
import swdchatbox.system.user.entity.User;

import java.util.UUID;

public final class EnrollmentSpecifications {

    private EnrollmentSpecifications() {
    }

    public static Specification<User> studentEnrolledInSubject(UUID subjectId) {
        return (root, query, cb) -> {
            if (subjectId == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            var enrollment = subquery.from(StudentSubject.class);
            subquery.select(cb.literal(1L));
            subquery.where(
                    cb.equal(enrollment.get("student").get("id"), root.get("id")),
                    cb.equal(enrollment.get("subject").get("id"), subjectId)
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<User> lecturerEnrolledInSubject(UUID subjectId) {
        return (root, query, cb) -> {
            if (subjectId == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            var enrollment = subquery.from(LecturerSubject.class);
            subquery.select(cb.literal(1L));
            subquery.where(
                    cb.equal(enrollment.get("lecturer").get("id"), root.get("id")),
                    cb.equal(enrollment.get("subject").get("id"), subjectId)
            );
            return cb.exists(subquery);
        };
    }
}
