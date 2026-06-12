package swdchatbox.system.enrollment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.system.enrollment.entity.StudentSubject;
import swdchatbox.system.subject.entity.Subject;

import java.util.List;
import java.util.UUID;

public interface StudentSubjectRepository extends JpaRepository<StudentSubject, UUID> {

    List<StudentSubject> findAllByStudent_Id(UUID studentId);

    @Query("SELECT ss.subject FROM StudentSubject ss WHERE ss.student.id = :studentId ORDER BY ss.subject.name ASC")
    List<Subject> findSubjectsByStudent_Id(@Param("studentId") UUID studentId);

    @Query("SELECT ss FROM StudentSubject ss JOIN FETCH ss.subject WHERE ss.student.id IN :studentIds")
    List<StudentSubject> findAllByStudent_IdIn(@Param("studentIds") List<UUID> studentIds);

    boolean existsByStudent_Id(UUID studentId);

    boolean existsByStudent_IdAndSubject_Id(UUID studentId, UUID subjectId);

    void deleteAllByStudent_Id(UUID studentId);

    void deleteByStudent_IdAndSubject_Id(UUID studentId, UUID subjectId);
}
