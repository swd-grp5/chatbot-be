package swdchatbox.modules.quiz.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.repository.DocumentRepository;
import swdchatbox.modules.enrollment.service.SubjectEnrollmentService;
import swdchatbox.modules.quiz.QuestionTypeCodes;
import swdchatbox.modules.quiz.dto.request.BankQuestionCreateRequest;
import swdchatbox.modules.quiz.dto.request.BankQuestionFilterRequest;
import swdchatbox.modules.quiz.dto.request.BankQuestionOptionRequest;
import swdchatbox.modules.quiz.dto.request.BankQuestionUpdateRequest;
import swdchatbox.modules.quiz.dto.request.QuizOptionRequest;
import swdchatbox.modules.quiz.dto.request.QuizQuestionRequest;
import swdchatbox.modules.quiz.dto.response.BankQuestionResponse;
import swdchatbox.modules.quiz.entity.BankQuestion;
import swdchatbox.modules.quiz.entity.BankQuestionOption;
import swdchatbox.modules.quiz.entity.QuestionType;
import swdchatbox.modules.quiz.entity.QuizOption;
import swdchatbox.modules.quiz.entity.QuizQuestion;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;
import swdchatbox.modules.quiz.mapper.BankQuestionMapper;
import swdchatbox.modules.quiz.repository.BankQuestionRepository;
import swdchatbox.modules.quiz.repository.BankQuestionSpecifications;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;
import swdchatbox.shared.dto.PageResponse;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankQuestionService {

    private final BankQuestionRepository bankQuestionRepository;
    private final UserRepository userRepository;
    private final SubjectEnrollmentService subjectEnrollmentService;
    private final QuestionTypeService questionTypeService;
    private final DocumentRepository documentRepository;

    public PageResponse<BankQuestionResponse> findAll(BankQuestionFilterRequest filter, Pageable pageable, String userEmail) {
        User user = resolveUser(userEmail);
        Specification<BankQuestion> spec = buildSpecification(filter, user);
        Page<BankQuestion> page = bankQuestionRepository.findAll(spec, pageable);
        return PageResponse.<BankQuestionResponse>builder()
                .content(page.getContent().stream().map(BankQuestionMapper::toResponse).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    public BankQuestionResponse findById(UUID id, String userEmail) {
        User user = resolveUser(userEmail);
        BankQuestion question = findBankQuestion(id);
        requireCanManage(user, question.getSubject().getId());
        return BankQuestionMapper.toResponse(question);
    }

    @Transactional
    public BankQuestionResponse create(BankQuestionCreateRequest request, String userEmail) {
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, request.getSubjectId());
        Subject subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());

        QuestionType questionType = questionTypeService.findActiveQuestionType(request.getQuestionTypeId());
        requireMultipleChoice(questionType);
        validateOptions(request.getMultipleChoiceMode(), request.getOptions());

        BankQuestion question = BankQuestion.builder()
                .subject(subject)
                .createdBy(lecturer)
                .questionType(questionType)
                .multipleChoiceMode(request.getMultipleChoiceMode())
                .questionText(request.getQuestionText().trim())
                .defaultPoints(request.getDefaultPoints())
                .sourceDocument(resolveSourceDocument(request.getSourceDocumentId(), subject))
                .sourceExcerpt(request.getSourceExcerpt())
                .aiGenerated(false)
                .active(true)
                .build();
        question.setOptions(buildOptions(question, request.getOptions()));
        return BankQuestionMapper.toResponse(bankQuestionRepository.save(question));
    }

    @Transactional
    public BankQuestionResponse update(UUID id, BankQuestionUpdateRequest request, String userEmail) {
        User lecturer = resolveUser(userEmail);
        BankQuestion question = findBankQuestion(id);
        requireCanManage(lecturer, question.getSubject().getId());

        QuestionType questionType = questionTypeService.findActiveQuestionType(request.getQuestionTypeId());
        requireMultipleChoice(questionType);
        validateOptions(request.getMultipleChoiceMode(), request.getOptions());

        question.setQuestionType(questionType);
        question.setMultipleChoiceMode(request.getMultipleChoiceMode());
        question.setQuestionText(request.getQuestionText().trim());
        question.setDefaultPoints(request.getDefaultPoints());
        question.setSourceExcerpt(request.getSourceExcerpt());
        question.getOptions().clear();
        question.getOptions().addAll(buildOptions(question, request.getOptions()));
        return BankQuestionMapper.toResponse(bankQuestionRepository.save(question));
    }

    @Transactional
    public void delete(UUID id, String userEmail) {
        User lecturer = resolveUser(userEmail);
        BankQuestion question = findBankQuestion(id);
        requireCanManage(lecturer, question.getSubject().getId());
        bankQuestionRepository.delete(question);
    }

    @Transactional
    public BankQuestionResponse toggleActive(UUID id, String userEmail) {
        User lecturer = resolveUser(userEmail);
        BankQuestion question = findBankQuestion(id);
        requireCanManage(lecturer, question.getSubject().getId());
        question.setActive(question.getActive() == null || !question.getActive());
        return BankQuestionMapper.toResponse(bankQuestionRepository.save(question));
    }

    /**
     * Lưu một câu hỏi của quiz vào ngân hàng câu hỏi (copy). Trả về bản ghi bank đã lưu để quiz gắn link.
     */
    @Transactional
    public BankQuestion saveFromQuizQuestion(QuizQuestion q, Subject subject, User lecturer, boolean aiGenerated) {
        BankQuestion bank = BankQuestion.builder()
                .subject(subject)
                .createdBy(lecturer)
                .questionType(q.getQuestionType())
                .multipleChoiceMode(q.getMultipleChoiceMode())
                .questionText(q.getQuestionText())
                .defaultPoints(q.getPoints())
                .sourceDocument(q.getSourceDocument())
                .sourceExcerpt(q.getSourceExcerpt())
                .aiGenerated(aiGenerated)
                .active(true)
                .build();
        Set<BankQuestionOption> options = new LinkedHashSet<>();
        for (QuizOption o : q.getOptions()) {
            options.add(BankQuestionOption.builder()
                    .bankQuestion(bank)
                    .optionText(o.getOptionText())
                    .isCorrect(o.getIsCorrect())
                    .sortOrder(o.getSortOrder())
                    .build());
        }
        bank.setOptions(options);
        return bankQuestionRepository.save(bank);
    }

    /**
     * Lưu các câu hỏi do AI sinh trực tiếp vào ngân hàng (aiGenerated=true).
     * Dùng bởi luồng "AI sinh câu hỏi vào ngân hàng" (không tạo quiz).
     */
    @Transactional
    public List<BankQuestionResponse> saveGenerated(Subject subject, User lecturer,
                                                    List<QuizQuestionRequest> questions, Double defaultPointsOverride) {
        List<BankQuestion> saved = new ArrayList<>();
        for (QuizQuestionRequest q : questions) {
            QuestionType questionType = questionTypeService.findActiveQuestionType(q.getQuestionTypeId());
            requireMultipleChoice(questionType);

            BankQuestion bank = BankQuestion.builder()
                    .subject(subject)
                    .createdBy(lecturer)
                    .questionType(questionType)
                    .multipleChoiceMode(q.getMultipleChoiceMode())
                    .questionText(q.getQuestionText().trim())
                    .defaultPoints(defaultPointsOverride != null ? defaultPointsOverride : q.getPoints())
                    .sourceDocument(resolveSourceDocument(q.getSourceDocumentId(), subject))
                    .sourceExcerpt(q.getSourceExcerpt())
                    .aiGenerated(true)
                    .active(true)
                    .build();

            Set<BankQuestionOption> options = new LinkedHashSet<>();
            for (QuizOptionRequest o : q.getOptions()) {
                options.add(BankQuestionOption.builder()
                        .bankQuestion(bank)
                        .optionText(o.getOptionText().trim())
                        .isCorrect(o.getIsCorrect())
                        .sortOrder(o.getSortOrder())
                        .build());
            }
            bank.setOptions(options);
            saved.add(bankQuestionRepository.save(bank));
        }
        return saved.stream().map(BankQuestionMapper::toResponse).toList();
    }

    /** Lấy một câu hỏi bank và đảm bảo nó thuộc đúng môn học (dùng khi quiz gắn link). */
    public BankQuestion requireForSubject(UUID id, UUID subjectId) {
        BankQuestion b = findBankQuestion(id);
        if (b.getSubject() == null || !b.getSubject().getId().equals(subjectId)) {
            throw new BadRequestException("Bank question does not belong to this subject: " + id);
        }
        return b;
    }

    /** Lấy pool câu hỏi cho việc lắp ráp quiz, giữ thứ tự theo ids và kiểm tra hợp lệ. */
    public List<BankQuestion> getPoolForAssembly(List<UUID> ids, UUID subjectId) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("bankQuestionIds are required");
        }
        Set<UUID> unique = new LinkedHashSet<>(ids);
        List<BankQuestion> found = bankQuestionRepository.findAllByIdIn(new ArrayList<>(unique));
        Map<UUID, BankQuestion> byId = found.stream()
                .collect(Collectors.toMap(BankQuestion::getId, b -> b));

        List<BankQuestion> pool = new ArrayList<>();
        for (UUID id : unique) {
            BankQuestion b = byId.get(id);
            if (b == null) {
                throw new BadRequestException("Bank question not found: " + id);
            }
            if (b.getSubject() == null || !b.getSubject().getId().equals(subjectId)) {
                throw new BadRequestException("Bank question does not belong to this subject: " + id);
            }
            if (!Boolean.TRUE.equals(b.getActive())) {
                throw new BadRequestException("Bank question is not active: " + id);
            }
            if (b.getOptions() == null || b.getOptions().isEmpty()) {
                throw new BadRequestException("Bank question has no options: " + id);
            }
            pool.add(b);
        }
        return pool;
    }

    private Specification<BankQuestion> buildSpecification(BankQuestionFilterRequest filter, User user) {
        String roleCode = user.getRole().getCode();
        Specification<BankQuestion> spec = Specification
                .where(BankQuestionSpecifications.hasSubjectId(filter != null ? filter.getSubjectId() : null))
                .and(BankQuestionSpecifications.hasQuestionTypeId(filter != null ? filter.getQuestionTypeId() : null))
                .and(BankQuestionSpecifications.hasMode(filter != null ? filter.getMode() : null))
                .and(BankQuestionSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(BankQuestionSpecifications.hasAiGenerated(filter != null ? filter.getAiGenerated() : null))
                .and(BankQuestionSpecifications.keywordLike(filter != null ? filter.getKeyword() : null));

        if (RoleCodes.LECTURER.equals(roleCode)) {
            List<UUID> subjectIds = subjectEnrollmentService.getLecturerSubjectIds(user.getId());
            spec = spec.and(BankQuestionSpecifications.subjectIdIn(subjectIds));
        } else if (!RoleCodes.ADMIN.equals(roleCode)) {
            throw new BadRequestException("Only lecturers or admins can access the question bank");
        }
        return spec;
    }

    private void requireCanManage(User user, UUID subjectId) {
        subjectEnrollmentService.requireLecturerCanManageQuiz(user, subjectId);
    }

    private void requireMultipleChoice(QuestionType questionType) {
        if (!QuestionTypeCodes.MULTIPLE_CHOICE.equals(questionType.getCode())) {
            throw new BadRequestException("Only multiple choice questions are supported");
        }
    }

    private Set<BankQuestionOption> buildOptions(BankQuestion question, List<BankQuestionOptionRequest> requests) {
        return requests.stream()
                .map(o -> BankQuestionOption.builder()
                        .bankQuestion(question)
                        .optionText(o.getOptionText().trim())
                        .isCorrect(o.getIsCorrect())
                        .sortOrder(o.getSortOrder())
                        .build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateOptions(MultipleChoiceMode mode, List<BankQuestionOptionRequest> options) {
        if (mode == null) {
            throw new BadRequestException("multipleChoiceMode is required");
        }
        if (options == null || options.isEmpty()) {
            throw new BadRequestException("options are required");
        }
        long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();
        if (options.size() < 2) {
            throw new BadRequestException("questions must have at least 2 options");
        }
        switch (mode) {
            case SINGLE -> {
                if (correctCount != 1) {
                    throw new BadRequestException("single choice questions must have exactly 1 correct answer");
                }
            }
            case MULTIPLE -> {
                if (correctCount < 1) {
                    throw new BadRequestException("multiple select questions must have at least 1 correct answer");
                }
            }
        }
    }

    private BankQuestion findBankQuestion(UUID id) {
        return bankQuestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank question not found"));
    }

    private Document resolveSourceDocument(UUID documentId, Subject subject) {
        if (documentId == null) {
            return null;
        }
        return documentRepository.findById(documentId)
                .filter(doc -> doc.getSubject() != null
                        && subject != null
                        && subject.getId().equals(doc.getSubject().getId()))
                .orElse(null);
    }

    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("Authenticated user is required");
        }
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
