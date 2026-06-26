package swdchatbox.modules.enrollment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.modules.enrollment.entity.UserSubject;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSubjectRepository extends JpaRepository<UserSubject, UUID> {

    List<UserSubject> findAllByUser_IdOrderBySubject_NameAsc(UUID userId);

    List<UserSubject> findAllByUser_IdIn(Collection<UUID> userIds);

    List<UserSubject> findAllBySubject_Id(UUID subjectId);

    boolean existsByUser_IdAndSubject_Id(UUID userId, UUID subjectId);

    void deleteAllByUser_Id(UUID userId);

    @Query("""
            SELECT us FROM UserSubject us
            JOIN us.user u
            JOIN u.role r
            WHERE us.subject.id = :subjectId
              AND r.code = 'LECTURER'
            """)
    Optional<UserSubject> findLecturerAssignmentBySubjectId(@Param("subjectId") UUID subjectId);

}
