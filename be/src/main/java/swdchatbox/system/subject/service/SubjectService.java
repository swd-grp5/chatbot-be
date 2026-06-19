package swdchatbox.system.subject.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.enrollment.service.SubjectEnrollmentService;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.subject.dto.request.SubjectFilterRequest;
import swdchatbox.system.subject.dto.request.SubjectRequest;
import swdchatbox.system.subject.dto.response.SubjectResponse;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.mapper.SubjectMapper;
import swdchatbox.system.subject.repository.SubjectRepository;
import swdchatbox.system.subject.repository.SubjectSpecifications;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final SubjectEnrollmentService subjectEnrollmentService;

    public PageResponse<SubjectResponse> findAll(SubjectFilterRequest filter, Pageable pageable) {
        Specification<Subject> spec = Specification
                .where(SubjectSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(SubjectSpecifications.keywordLike(filter != null ? filter.getKeyword() : null));

        Page<Subject> page = subjectRepository.findAll(spec, pageable);
        return PageResponse.<SubjectResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    public SubjectResponse findById(UUID id) {
        return toResponse(findSubject(id));
    }

    @Transactional
    public SubjectResponse create(SubjectRequest request) {
        validateUniqueCode(request.getCode(), null);
        Subject subject = Subject.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .active(request.getActive() == null ? true : request.getActive())
                .build();
        subject = subjectRepository.save(subject);
        if (request.getUserId() != null) {
            applyUserAssignment(subject, request.getUserId());
        }
        return toResponse(subject);
    }

    @Transactional
    public SubjectResponse update(UUID id, SubjectRequest request) {
        Subject subject = findSubject(id);
        validateUniqueCode(request.getCode(), id);
        subject.setCode(request.getCode());
        subject.setName(request.getName());
        subject.setDescription(request.getDescription());
        if (request.getActive() != null) {
            subject.setActive(request.getActive());
        }
        if (request.getUserId() != null) {
            applyUserAssignment(subject, request.getUserId());
        }
        subject = subjectRepository.save(subject);
        return toResponse(subject);
    }

    @Transactional
    public void delete(UUID id) {
        Subject subject = findSubject(id);
        subjectRepository.delete(subject);
    }

    @Transactional
    public SubjectResponse toggleActive(UUID id) {
        Subject subject = findSubject(id);
        subject.setActive(subject.getActive() == null || !subject.getActive());
        subject = subjectRepository.save(subject);
        return toResponse(subject);
    }

    private SubjectResponse toResponse(Subject subject) {
        User assignedLecturer = subjectEnrollmentService.findAssignedLecturerForSubject(subject.getId()).orElse(null);
        return SubjectMapper.toResponse(subject, assignedLecturer);
    }

    private Subject findSubject(UUID id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
    }

    private void validateUniqueCode(String code, UUID excludeId) {
        boolean duplicate = excludeId == null
                ? subjectRepository.existsByCode(code)
                : subjectRepository.existsByCodeAndIdNot(code, excludeId);
        if (duplicate) {
            throw new BadRequestException("Subject code already exists");
        }
    }

    private void applyUserAssignment(Subject subject, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!RoleCodes.LECTURER.equals(user.getRole().getCode())) {
            throw new BadRequestException("Assigned user must be a lecturer");
        }
        subjectEnrollmentService.assignUserToSubject(subject, user);
    }
}
