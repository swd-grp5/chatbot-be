package swdchatbox.modules.quiz.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import swdchatbox.modules.quiz.QuestionTypeCodes;
import swdchatbox.modules.quiz.dto.request.QuestionTypeFilterRequest;
import swdchatbox.modules.quiz.dto.request.QuestionTypeRequest;
import swdchatbox.modules.quiz.dto.response.QuestionTypeResponse;
import swdchatbox.modules.quiz.entity.QuestionType;
import swdchatbox.modules.quiz.mapper.QuestionTypeMapper;
import swdchatbox.modules.quiz.repository.QuestionTypeRepository;
import swdchatbox.modules.quiz.repository.QuestionTypeSpecifications;
import swdchatbox.modules.quiz.repository.QuizQuestionRepository;
import swdchatbox.shared.dto.PageResponse;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionTypeService {

    private final QuestionTypeRepository questionTypeRepository;
    private final QuizQuestionRepository quizQuestionRepository;

    public PageResponse<QuestionTypeResponse> findAll(QuestionTypeFilterRequest filter, Pageable pageable) {
        Specification<QuestionType> spec = Specification
                .where(QuestionTypeSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(QuestionTypeSpecifications.keywordLike(filter != null ? filter.getKeyword() : null));
        Page<QuestionType> page = questionTypeRepository.findAll(spec, pageable);
        return PageResponse.<QuestionTypeResponse>builder()
                .content(page.getContent().stream().map(QuestionTypeMapper::toResponse).toList())
                .page(page.getNumber()).pageSize(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast()).empty(page.isEmpty())
                .build();
    }

    public List<QuestionTypeResponse> findAllActive() {
        return questionTypeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(QuestionTypeMapper::toResponse).toList();
    }

    public QuestionTypeResponse findById(UUID id) {
        return QuestionTypeMapper.toResponse(find(id));
    }

    public QuestionType findActiveQuestionType(UUID id) {
        QuestionType type = find(id);
        if (!Boolean.TRUE.equals(type.getActive())) {
            throw new BadRequestException("Question type is not active: " + type.getCode());
        }
        return type;
    }

    @Transactional
    public QuestionTypeResponse create(QuestionTypeRequest request) {
        String code = normalizeCode(request.getCode());
        if (questionTypeRepository.existsByCode(code)) {
            throw new BadRequestException("Question type code already exists");
        }
        return QuestionTypeMapper.toResponse(questionTypeRepository.save(QuestionType.builder()
                .code(code).name(request.getName().trim()).description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .active(request.getActive() == null || request.getActive()).build()));
    }

    @Transactional
    public QuestionTypeResponse update(UUID id, QuestionTypeRequest request) {
        QuestionType type = find(id);
        String code = normalizeCode(request.getCode());
        if (questionTypeRepository.existsByCodeAndIdNot(code, id)) {
            throw new BadRequestException("Question type code already exists");
        }
        if (isSystem(type.getCode()) && !type.getCode().equals(code)) {
            throw new BadRequestException("System question type code cannot be changed");
        }
        type.setCode(code);
        type.setName(request.getName().trim());
        type.setDescription(request.getDescription());
        if (request.getSortOrder() != null) type.setSortOrder(request.getSortOrder());
        if (request.getActive() != null) type.setActive(request.getActive());
        return QuestionTypeMapper.toResponse(questionTypeRepository.save(type));
    }

    @Transactional
    public void delete(UUID id) {
        QuestionType type = find(id);
        if (isSystem(type.getCode())) throw new BadRequestException("System question types cannot be deleted");
        if (quizQuestionRepository.countByQuestionType_Id(id) > 0) {
            throw new BadRequestException("Question type is in use by quiz questions");
        }
        questionTypeRepository.delete(type);
    }

    @Transactional
    public QuestionTypeResponse toggleActive(UUID id) {
        QuestionType type = find(id);
        if (isSystem(type.getCode()) && Boolean.TRUE.equals(type.getActive())) {
            throw new BadRequestException("System question types cannot be deactivated");
        }
        type.setActive(type.getActive() == null || !type.getActive());
        return QuestionTypeMapper.toResponse(questionTypeRepository.save(type));
    }

    private QuestionType find(UUID id) {
        return questionTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question type not found"));
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) throw new BadRequestException("Question type code is required");
        return code.trim().toUpperCase();
    }

    private boolean isSystem(String code) {
        return QuestionTypeCodes.MULTIPLE_CHOICE.equals(code);
    }
}
