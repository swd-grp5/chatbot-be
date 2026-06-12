package swdchatbox.system.enrollment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.system.enrollment.entity.LecturerSubject;
import swdchatbox.system.subject.entity.Subject;

import java.util.List;
import java.util.UUID;

public interface LecturerSubjectRepository extends JpaRepository<LecturerSubject, UUID> {

    List<LecturerSubject> findAllByLecturer_Id(UUID lecturerId);

    @Query("SELECT ls.subject FROM LecturerSubject ls WHERE ls.lecturer.id = :lecturerId ORDER BY ls.subject.name ASC")
    List<Subject> findSubjectsByLecturer_Id(@Param("lecturerId") UUID lecturerId);

    @Query("SELECT ls FROM LecturerSubject ls JOIN FETCH ls.subject WHERE ls.lecturer.id IN :lecturerIds")
    List<LecturerSubject> findAllByLecturer_IdIn(@Param("lecturerIds") List<UUID> lecturerIds);

    @Query("SELECT ls.subject.id FROM LecturerSubject ls WHERE ls.lecturer.id = :lecturerId")
    List<UUID> findSubjectIdsByLecturer_Id(@Param("lecturerId") UUID lecturerId);

    boolean existsByLecturer_Id(UUID lecturerId);

    boolean existsByLecturer_IdAndSubject_Id(UUID lecturerId, UUID subjectId);

    void deleteAllByLecturer_Id(UUID lecturerId);
}
