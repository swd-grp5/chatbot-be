package swdchatbox.system.enrollment.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import swdchatbox.system.enrollment.entity.LecturerSubject;
import swdchatbox.system.enrollment.entity.StudentSubject;
import swdchatbox.system.enrollment.repository.LecturerSubjectRepository;
import swdchatbox.system.enrollment.repository.StudentSubjectRepository;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.subject.dto.response.SubjectSummaryResponse;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.repository.SubjectRepository;
import swdchatbox.system.user.entity.User;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubjectEnrollmentService {

    private final StudentSubjectRepository studentSubjectRepository;
    private final LecturerSubjectRepository lecturerSubjectRepository;
    private final SubjectRepository subjectRepository;

    @Transactional
    public void replaceStudentSubjects(User student, List<UUID> subjectIds) {
        validateSubjectIds(subjectIds, true);
        studentSubjectRepository.deleteAllByStudent_Id(student.getId());
        saveStudentSubjectEnrollments(student, subjectIds);
    }

    /** Đồng bộ danh sách môn: mảng gửi lên = danh sách cuối cùng (thêm thiếu, gỡ dư). */
    @Transactional
    public void syncStudentSubjects(User student, List<UUID> subjectIds) {
        validateSubjectIds(subjectIds, true);
        Set<UUID> targetIds = new LinkedHashSet<>(subjectIds);
        Set<UUID> currentIds = new HashSet<>(getStudentSubjectIds(student.getId()));

        for (UUID subjectId : currentIds) {
            if (!targetIds.contains(subjectId)) {
                studentSubjectRepository.deleteByStudent_IdAndSubject_Id(student.getId(), subjectId);
            }
        }

        List<UUID> toAdd = targetIds.stream().filter(id -> !currentIds.contains(id)).toList();
        if (!toAdd.isEmpty()) {
            saveStudentSubjectEnrollments(student, toAdd);
        }
    }

    @Transactional
    public void replaceLecturerSubjects(User lecturer, List<UUID> subjectIds) {
        validateSubjectIds(subjectIds, true);
        lecturerSubjectRepository.deleteAllByLecturer_Id(lecturer.getId());
        saveLecturerSubjectEnrollments(lecturer, subjectIds);
    }

    /** Đồng bộ danh sách môn: mảng gửi lên = danh sách cuối cùng (thêm thiếu, gỡ dư). */
    @Transactional
    public void syncLecturerSubjects(User lecturer, List<UUID> subjectIds) {
        validateSubjectIds(subjectIds, true);
        Set<UUID> targetIds = new LinkedHashSet<>(subjectIds);
        Set<UUID> currentIds = new HashSet<>(getLecturerSubjectIds(lecturer.getId()));

        for (UUID subjectId : currentIds) {
            if (!targetIds.contains(subjectId)) {
                lecturerSubjectRepository.deleteByLecturer_IdAndSubject_Id(lecturer.getId(), subjectId);
            }
        }

        List<UUID> toAdd = targetIds.stream().filter(id -> !currentIds.contains(id)).toList();
        if (!toAdd.isEmpty()) {
            saveLecturerSubjectEnrollments(lecturer, toAdd);
        }
    }

    @Transactional
    public void deleteStudentSubjects(UUID studentId) {
        studentSubjectRepository.deleteAllByStudent_Id(studentId);
    }

    @Transactional
    public void deleteLecturerSubjects(UUID lecturerId) {
        lecturerSubjectRepository.deleteAllByLecturer_Id(lecturerId);
    }

    public List<SubjectSummaryResponse> getStudentSubjects(UUID studentId) {
        return studentSubjectRepository.findSubjectsByStudent_Id(studentId).stream()
                .map(this::toSummary)
                .toList();
    }

    public List<SubjectSummaryResponse> getLecturerSubjects(UUID lecturerId) {
        return lecturerSubjectRepository.findSubjectsByLecturer_Id(lecturerId).stream()
                .map(this::toSummary)
                .toList();
    }

    public Map<UUID, List<SubjectSummaryResponse>> getStudentSubjectsByStudentIds(List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        return studentSubjectRepository.findAllByStudent_IdIn(studentIds).stream()
                .collect(Collectors.groupingBy(
                        ss -> ss.getStudent().getId(),
                        Collectors.mapping(ss -> toSummary(ss.getSubject()), Collectors.toList())
                ));
    }

    public Map<UUID, List<SubjectSummaryResponse>> getLecturerSubjectsByLecturerIds(List<UUID> lecturerIds) {
        if (lecturerIds == null || lecturerIds.isEmpty()) {
            return Map.of();
        }
        return lecturerSubjectRepository.findAllByLecturer_IdIn(lecturerIds).stream()
                .collect(Collectors.groupingBy(
                        ls -> ls.getLecturer().getId(),
                        Collectors.mapping(ls -> toSummary(ls.getSubject()), Collectors.toList())
                ));
    }

    public void requireLecturerCanUpload(User user, UUID subjectId) {
        if (user == null) {
            throw new BadRequestException("Authenticated user is required to upload documents");
        }
        if (RoleCodes.ADMIN.equals(user.getRole().getCode())) {
            return;
        }
        if (!RoleCodes.LECTURER.equals(user.getRole().getCode())) {
            throw new BadRequestException("Only lecturers can upload documents");
        }
        if (!lecturerSubjectRepository.existsByLecturer_IdAndSubject_Id(user.getId(), subjectId)) {
            throw new BadRequestException("You are not enrolled to upload documents for this subject");
        }
    }

    public void requireStudentCanAccessSubject(User user, UUID subjectId) {
        if (user == null) {
            throw new BadRequestException("Authenticated user is required");
        }
        if (RoleCodes.ADMIN.equals(user.getRole().getCode())) {
            return;
        }
        if (!RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            throw new BadRequestException("Only students can use subject-based chat");
        }
        if (subjectId == null) {
            throw new BadRequestException("Subject is required");
        }
        if (!studentSubjectRepository.existsByStudent_IdAndSubject_Id(user.getId(), subjectId)) {
            throw new BadRequestException("You are not enrolled in this subject");
        }
    }

    public List<UUID> getLecturerSubjectIds(UUID lecturerId) {
        return lecturerSubjectRepository.findSubjectIdsByLecturer_Id(lecturerId);
    }

    public List<UUID> getStudentSubjectIds(UUID studentId) {
        return studentSubjectRepository.findSubjectsByStudent_Id(studentId).stream()
                .map(Subject::getId)
                .toList();
    }

    public Subject findActiveSubject(UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        if (!Boolean.TRUE.equals(subject.getActive())) {
            throw new BadRequestException("Subject is not active");
        }
        return subject;
    }

    private void saveStudentSubjectEnrollments(User student, List<UUID> subjectIds) {
        List<Subject> subjects = subjectRepository.findAllById(subjectIds);
        for (Subject subject : subjects) {
            studentSubjectRepository.save(StudentSubject.builder()
                    .student(student)
                    .subject(subject)
                    .build());
        }
    }

    private void saveLecturerSubjectEnrollments(User lecturer, List<UUID> subjectIds) {
        List<Subject> subjects = subjectRepository.findAllById(subjectIds);
        for (Subject subject : subjects) {
            lecturerSubjectRepository.save(LecturerSubject.builder()
                    .lecturer(lecturer)
                    .subject(subject)
                    .build());
        }
    }

    private void validateSubjectIds(List<UUID> subjectIds, boolean requireAtLeastOne) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            if (requireAtLeastOne) {
                throw new BadRequestException("At least one subject must be enrolled");
            }
            return;
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(subjectIds);
        if (uniqueIds.size() != subjectIds.size()) {
            throw new BadRequestException("Duplicate subject IDs are not allowed");
        }

        List<Subject> subjects = subjectRepository.findAllById(uniqueIds);
        if (subjects.size() != uniqueIds.size()) {
            throw new BadRequestException("One or more subjects were not found");
        }

        for (Subject subject : subjects) {
            if (!Boolean.TRUE.equals(subject.getActive())) {
                throw new BadRequestException("Subject is not active: " + subject.getCode());
            }
        }
    }

    private SubjectSummaryResponse toSummary(Subject subject) {
        return SubjectSummaryResponse.builder()
                .id(subject.getId())
                .code(subject.getCode())
                .name(subject.getName())
                .build();
    }
}
