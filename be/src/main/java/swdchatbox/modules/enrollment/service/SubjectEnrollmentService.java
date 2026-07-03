package swdchatbox.modules.enrollment.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.document.repository.DocumentRepository;
import swdchatbox.modules.enrollment.entity.UserSubject;
import swdchatbox.modules.enrollment.repository.UserSubjectRepository;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.modules.subject.dto.response.SubjectSummaryResponse;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.subject.repository.SubjectRepository;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubjectEnrollmentService {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final UserSubjectRepository userSubjectRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public void replaceStudentSubjects(User student, List<UUID> subjectIds) {
        validateStudent(student);
        replaceUserSubjects(student, subjectIds);
    }

    @Transactional
    public void syncStudentSubjects(User student, List<UUID> subjectIds) {
        validateStudent(student);
        syncUserSubjects(student, subjectIds);
    }

    @Transactional
    public void replaceLecturerSubjects(User lecturer, List<UUID> subjectIds) {
        syncLecturerSubjects(lecturer, subjectIds);
    }

    @Transactional
    public void syncLecturerSubjects(User lecturer, List<UUID> subjectIds) {
        validateLecturer(lecturer);
        validateSubjectIds(subjectIds, true);

        Set<UUID> targetIds = new LinkedHashSet<>(subjectIds);
        List<UserSubject> current = userSubjectRepository.findAllByUser_IdOrderBySubject_NameAsc(lecturer.getId());

        for (UserSubject assignment : current) {
            if (!targetIds.contains(assignment.getSubject().getId())) {
                userSubjectRepository.delete(assignment);
            }
        }

        for (UUID subjectId : targetIds) {
            assignLecturerToSubject(lecturer, subjectId);
        }
    }

    @Transactional
    public void deleteStudentSubjects(UUID userId) {
        userSubjectRepository.deleteAllByUser_Id(userId);
    }

    @Transactional
    public void deleteLecturerSubjects(UUID userId) {
        userSubjectRepository.deleteAllByUser_Id(userId);
    }

    @Transactional
    public void assignUserToSubject(Subject subject, User user) {
        validateLecturer(user);
        ensureSubjectAvailableForLecturer(subject.getId(), user.getId());
        ensureAssignment(user, subject);
    }

    public Optional<User> findAssignedLecturerForSubject(UUID subjectId) {
        return userSubjectRepository.findLecturerAssignmentBySubjectId(subjectId)
                .map(UserSubject::getUser);
    }

    public List<SubjectSummaryResponse> getStudentSubjects(UUID userId) {
        return toSummariesFromAssignments(userSubjectRepository.findAllByUser_IdOrderBySubject_NameAsc(userId));
    }

    public List<SubjectSummaryResponse> getLecturerSubjects(UUID userId) {
        return getStudentSubjects(userId);
    }

    public Map<UUID, List<SubjectSummaryResponse>> getStudentSubjectsByStudentIds(List<UUID> userIds) {
        return getSubjectsByUserIds(userIds);
    }

    public Map<UUID, List<SubjectSummaryResponse>> getLecturerSubjectsByLecturerIds(List<UUID> lecturerIds) {
        return getSubjectsByUserIds(lecturerIds);
    }

    public void requireLecturerCanUpload(User user, UUID subjectId) {
        requireLecturerCanManageSubject(user, subjectId, "upload documents");
    }

    public void requireLecturerCanManageQuiz(User user, UUID subjectId) {
        requireLecturerCanManageSubject(user, subjectId, "manage quizzes");
    }

    private void requireLecturerCanManageSubject(User user, UUID subjectId, String action) {
        if (user == null) {
            throw new BadRequestException("Authenticated user is required to " + action);
        }
        if (RoleCodes.ADMIN.equals(user.getRole().getCode())) {
            return;
        }
        if (!RoleCodes.LECTURER.equals(user.getRole().getCode())) {
            throw new BadRequestException("Only lecturers can " + action);
        }
        if (!userSubjectRepository.existsByUser_IdAndSubject_Id(user.getId(), subjectId)) {
            throw new BadRequestException("You are not assigned to " + action + " for this subject");
        }
        findActiveSubject(subjectId);
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
        if (!userSubjectRepository.existsByUser_IdAndSubject_Id(user.getId(), subjectId)) {
            throw new BadRequestException("You are not enrolled in this subject");
        }
    }

    public List<UUID> getLecturerSubjectIds(UUID userId) {
        return getAssignedSubjectIds(userId);
    }

    public List<UUID> getStudentSubjectIds(UUID userId) {
        return getAssignedSubjectIds(userId);
    }

    public Subject findActiveSubject(UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        if (!Boolean.TRUE.equals(subject.getActive())) {
            throw new BadRequestException("Subject is not active");
        }
        return subject;
    }

    @Transactional
    public void replaceUserSubjects(User user, List<UUID> subjectIds) {
        validateSubjectIds(subjectIds, true);
        userSubjectRepository.deleteAllByUser_Id(user.getId());
        for (UUID subjectId : new LinkedHashSet<>(subjectIds)) {
            Subject subject = findActiveSubject(subjectId);
            ensureAssignment(user, subject);
        }
    }

    @Transactional
    public void syncUserSubjects(User user, List<UUID> subjectIds) {
        replaceUserSubjects(user, subjectIds);
    }

    public List<SubjectSummaryResponse> getUserSubjects(UUID userId) {
        return toSummariesFromAssignments(userSubjectRepository.findAllByUser_IdOrderBySubject_NameAsc(userId));
    }

    public Map<UUID, List<SubjectSummaryResponse>> getUserSubjectsByUserIds(List<UUID> userIds) {
        return getSubjectsByUserIds(userIds);
    }

    private Map<UUID, List<SubjectSummaryResponse>> getSubjectsByUserIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<UserSubject> assignments = userSubjectRepository.findAllByUser_IdIn(userIds);
        Set<UUID> subjectIds = assignments.stream()
                .map(assignment -> assignment.getSubject().getId())
                .collect(Collectors.toSet());
        Map<UUID, Long> documentCounts = getDocumentCountsBySubjectIds(subjectIds);

        Map<UUID, List<SubjectSummaryResponse>> result = new HashMap<>();
        for (UUID userId : userIds) {
            result.put(userId, new ArrayList<>());
        }

        for (UserSubject assignment : assignments) {
            UUID userId = assignment.getUser().getId();
            result.computeIfAbsent(userId, ignored -> new ArrayList<>())
                    .add(toSummary(assignment.getSubject(), documentCounts));
        }

        result.replaceAll((userId, summaries) -> summaries.stream()
                .sorted(Comparator.comparing(SubjectSummaryResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList());

        return result;
    }

    private void assignLecturerToSubject(User lecturer, UUID subjectId) {
        ensureSubjectAvailableForLecturer(subjectId, lecturer.getId());
        Subject subject = findActiveSubject(subjectId);
        ensureAssignment(lecturer, subject);
    }

    private void ensureAssignment(User user, Subject subject) {
        if (userSubjectRepository.existsByUser_IdAndSubject_Id(user.getId(), subject.getId())) {
            return;
        }
        userSubjectRepository.save(UserSubject.builder()
                .user(user)
                .subject(subject)
                .build());
    }

    private void ensureSubjectAvailableForLecturer(UUID subjectId, UUID lecturerId) {
        userSubjectRepository.findLecturerAssignmentBySubjectId(subjectId)
                .ifPresent(existing -> {
                    if (!existing.getUser().getId().equals(lecturerId)) {
                        Subject subject = existing.getSubject();
                        User assignedLecturer = existing.getUser();
                        throw new BadRequestException(
                                "Subject " + subject.getCode()
                                        + " is already assigned to lecturer "
                                        + assignedLecturer.getFullName()
                        );
                    }
                });
    }

    private List<UUID> getAssignedSubjectIds(UUID userId) {
        return userSubjectRepository.findAllByUser_IdOrderBySubject_NameAsc(userId).stream()
                .map(assignment -> assignment.getSubject().getId())
                .toList();
    }

    private List<SubjectSummaryResponse> toSummariesFromAssignments(List<UserSubject> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> documentCounts = getDocumentCountsBySubjectIds(
                assignments.stream().map(assignment -> assignment.getSubject().getId()).toList());
        return assignments.stream()
                .map(assignment -> toSummary(assignment.getSubject(), documentCounts))
                .toList();
    }

    private void validateStudent(User user) {
        if (user == null || user.getRole() == null) {
            throw new BadRequestException("Student is required");
        }
        if (!RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            throw new BadRequestException("User is not a student");
        }
    }

    private void validateLecturer(User user) {
        if (user == null || user.getRole() == null) {
            throw new BadRequestException("Lecturer is required");
        }
        if (!RoleCodes.LECTURER.equals(user.getRole().getCode())) {
            throw new BadRequestException("User is not a lecturer");
        }
    }

    private void validateSubjectIds(List<UUID> subjectIds, boolean requireAtLeastOne) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            if (requireAtLeastOne) {
                throw new BadRequestException("At least one subject must be assigned");
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

    private Map<UUID, Long> getDocumentCountsBySubjectIds(Collection<UUID> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : documentRepository.countBySubjectIds(subjectIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private SubjectSummaryResponse toSummary(Subject subject, Map<UUID, Long> documentCounts) {
        return SubjectSummaryResponse.builder()
                .id(subject.getId())
                .code(subject.getCode())
                .name(subject.getName())
                .totalDocuments(documentCounts.getOrDefault(subject.getId(), 0L))
                .build();
    }
}
