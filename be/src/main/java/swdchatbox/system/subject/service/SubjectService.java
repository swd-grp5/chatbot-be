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
import swdchatbox.system.subject.dto.request.SubjectFilterRequest;
import swdchatbox.system.subject.dto.request.SubjectRequest;
import swdchatbox.system.subject.dto.response.SubjectResponse;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.mapper.SubjectMapper;
import swdchatbox.system.subject.repository.SubjectRepository;
import swdchatbox.system.subject.repository.SubjectSpecifications;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public PageResponse<SubjectResponse> findAll(SubjectFilterRequest filter, Pageable pageable) {
        Specification<Subject> spec = Specification
                .where(SubjectSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(SubjectSpecifications.keywordLike(filter != null ? filter.getKeyword() : null));

        Page<Subject> page = subjectRepository.findAll(spec, pageable);
        return PageResponse.<SubjectResponse>builder()
                .content(page.getContent().stream().map(SubjectMapper::toResponse).toList())
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
        return SubjectMapper.toResponse(findSubject(id));
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
        return SubjectMapper.toResponse(subjectRepository.save(subject));
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
        return SubjectMapper.toResponse(subjectRepository.save(subject));
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
        return SubjectMapper.toResponse(subjectRepository.save(subject));
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
}
