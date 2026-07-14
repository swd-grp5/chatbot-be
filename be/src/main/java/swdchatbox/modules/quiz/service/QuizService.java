package swdchatbox.modules.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.repository.DocumentRepository;
import swdchatbox.modules.enrollment.service.SubjectEnrollmentService;
import swdchatbox.modules.quiz.dto.request.*;
import swdchatbox.modules.quiz.dto.response.*;
import swdchatbox.modules.quiz.entity.*;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;
import swdchatbox.modules.quiz.enums.PointsDistributionMode;
import swdchatbox.modules.quiz.enums.QuizStatus;
import swdchatbox.modules.quiz.mapper.QuizMapper;
import swdchatbox.modules.quiz.QuestionTypeCodes;
import swdchatbox.modules.quiz.repository.QuizAttemptRepository;
import swdchatbox.modules.quiz.repository.QuizRepository;
import swdchatbox.modules.quiz.repository.QuizSpecifications;
import swdchatbox.modules.quiz.repository.QuizVariantRepository;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.subscription.repository.UserSubscriptionRepository;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;
import swdchatbox.shared.dto.PageResponse;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizVariantRepository quizVariantRepository;
    private final UserRepository userRepository;
    private final SubjectEnrollmentService subjectEnrollmentService;
    private final QuestionTypeService questionTypeService;
    private final BankQuestionService bankQuestionService;
    private final DocumentRepository documentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ObjectMapper objectMapper;

    public PageResponse<QuizSummaryResponse> findAll(QuizFilterRequest filter, Pageable pageable, String userEmail) {
        User user = resolveUser(userEmail);
        Specification<Quiz> spec = buildSpecification(filter, user);

        Page<Quiz> page = quizRepository.findAll(spec, pageable);
        return PageResponse.<QuizSummaryResponse>builder()
                .content(page.getContent().stream().map(QuizMapper::toSummary).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    @Transactional
    public QuizResponse findById(UUID id, String userEmail, boolean forStudentAttempt) {
        Quiz quiz = findQuiz(id);
        User user = resolveUser(userEmail);
        validateQuizAccess(user, quiz, forStudentAttempt);
        boolean includeAnswers = !forStudentAttempt && !RoleCodes.STUDENT.equals(user.getRole().getCode());
        return QuizMapper.toResponse(quiz, includeAnswers, loadVariantSummaries(id));
    }

    private List<QuizVariantSummaryResponse> loadVariantSummaries(UUID quizId) {
        return quizVariantRepository.findAllByQuiz_IdOrderByVariantNumberAsc(quizId).stream()
                .map(QuizMapper::toVariantSummary)
                .toList();
    }

    @Transactional
    public QuizResponse create(QuizCreateRequest request, String userEmail) {
        return saveQuiz(request, userEmail, false);
    }

    @Transactional
    public QuizResponse saveQuiz(QuizCreateRequest request, String userEmail, boolean aiGenerated) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.getSubjectId() == null) {
            throw new BadRequestException("subjectId is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }

        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, request.getSubjectId());
        Subject subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());
        validateQuestions(request.getQuestions());

        Quiz quiz = Quiz.builder()
                .subject(subject)
                .createdBy(lecturer)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .status(QuizStatus.DRAFT)
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .active(true)
                .aiGenerated(aiGenerated)
                .build();

        applyQuestions(quiz, request.getQuestions(), true, aiGenerated);
        quiz = quizRepository.saveAndFlush(quiz);
        // Reload so @CreationTimestamp is populated in the response
        Quiz saved = quizRepository.findWithDetailsById(quiz.getId()).orElse(quiz);
        return QuizMapper.toResponse(saved, true);
    }

    @Transactional
    public QuizResponse assemble(QuizAssembleRequest request, String userEmail) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.getSubjectId() == null) {
            throw new BadRequestException("subjectId is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }

        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, request.getSubjectId());
        Subject subject = subjectEnrollmentService.findActiveSubject(request.getSubjectId());

        List<BankQuestion> pool = bankQuestionService.getPoolForAssembly(request.getBankQuestionIds(), subject.getId());
        int poolSize = pool.size();

        int variantCount = request.getVariantCount() != null ? request.getVariantCount() : 1;
        Integer perVariant = request.getQuestionsPerVariant();
        if (perVariant != null && perVariant > poolSize) {
            throw new BadRequestException("questionsPerVariant (" + perVariant + ") cannot exceed pool size (" + poolSize + ")");
        }

        PointsDistributionMode mode = request.getPointsMode() != null
                ? request.getPointsMode() : PointsDistributionMode.EVEN;
        Map<UUID, Double> pointsByBankId = resolveAssemblePoints(request, pool, perVariant, poolSize, mode);

        Quiz quiz = Quiz.builder()
                .subject(subject)
                .createdBy(lecturer)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .status(QuizStatus.DRAFT)
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .active(true)
                .aiGenerated(false)
                .shuffleQuestions(Boolean.TRUE.equals(request.getShuffleQuestions()))
                .shuffleOptions(Boolean.TRUE.equals(request.getShuffleOptions()))
                .showScore(!Boolean.FALSE.equals(request.getShowScore()))
                .questionsPerVariant(perVariant)
                .variantCount(variantCount)
                .build();

        int order = 0;
        for (BankQuestion b : pool) {
            QuizQuestion question = QuizQuestion.builder()
                    .quiz(quiz)
                    .questionType(b.getQuestionType())
                    .bankQuestion(b)
                    .multipleChoiceMode(b.getMultipleChoiceMode())
                    .questionText(b.getQuestionText())
                    .points(round2(pointsByBankId.get(b.getId())))
                    .sortOrder(order++)
                    .sourceDocument(b.getSourceDocument())
                    .sourceExcerpt(b.getSourceExcerpt())
                    .build();
            Set<QuizOption> options = b.getOptions().stream()
                    .sorted(Comparator.comparing(BankQuestionOption::getSortOrder))
                    .map(o -> QuizOption.builder()
                            .question(question)
                            .optionText(o.getOptionText())
                            .isCorrect(o.getIsCorrect())
                            .sortOrder(o.getSortOrder())
                            .build())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            question.setOptions(options);
            quiz.getQuestions().add(question);
        }

        quiz = quizRepository.saveAndFlush(quiz);
        Quiz saved = quizRepository.findWithDetailsById(quiz.getId()).orElse(quiz);
        generateVariants(saved);
        saved = quizRepository.saveAndFlush(saved);
        return QuizMapper.toResponse(saved, true, loadVariantSummaries(saved.getId()));
    }

    @Transactional
    public QuizResponse regenerateVariants(UUID id, String userEmail) {
        Quiz quiz = findQuiz(id);
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, quiz.getSubject().getId());
        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new BadRequestException("Variants can only be regenerated while the quiz is in DRAFT");
        }
        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            throw new BadRequestException("Quiz has no question pool to build variants from");
        }
        generateVariants(quiz);
        quiz = quizRepository.saveAndFlush(quiz);
        return QuizMapper.toResponse(quiz, true, loadVariantSummaries(quiz.getId()));
    }

    private Map<UUID, Double> resolveAssemblePoints(QuizAssembleRequest request, List<BankQuestion> pool,
                                                    Integer perVariant, int poolSize, PointsDistributionMode mode) {
        Map<UUID, Double> result = new HashMap<>();
        if (mode == PointsDistributionMode.CUSTOM) {
            Map<UUID, Double> custom = new HashMap<>();
            if (request.getCustomPoints() != null) {
                for (QuestionPointsItem item : request.getCustomPoints()) {
                    custom.put(item.getBankQuestionId(), item.getPoints());
                }
            }
            for (BankQuestion b : pool) {
                Double pts = custom.get(b.getId());
                if (pts == null) {
                    pts = b.getDefaultPoints();
                }
                if (pts == null || pts < 0.1) {
                    throw new BadRequestException("points (>= 0.1) is required for bank question: " + b.getId());
                }
                result.put(b.getId(), pts);
            }
        } else {
            double totalPoints = request.getTotalPoints() != null ? request.getTotalPoints() : 10.0;
            int effectiveCount = perVariant != null ? perVariant : poolSize;
            if (effectiveCount <= 0) {
                throw new BadRequestException("Cannot distribute points over zero questions");
            }
            double perPoint = totalPoints / effectiveCount;
            if (perPoint < 0.1) {
                throw new BadRequestException("totalPoints is too small; each question needs at least 0.1 points");
            }
            for (BankQuestion b : pool) {
                result.put(b.getId(), perPoint);
            }
        }
        return result;
    }

    /** Sinh lại toàn bộ đề (variant) từ pool câu hỏi hiện tại của quiz theo cấu hình xáo trộn. */
    private void generateVariants(Quiz quiz) {
        quiz.getVariants().clear();

        List<QuizQuestion> pool = new ArrayList<>(quiz.getQuestions());
        int poolSize = pool.size();
        int perVariant = quiz.getQuestionsPerVariant() != null
                ? Math.min(quiz.getQuestionsPerVariant(), poolSize) : poolSize;
        int count = quiz.getVariantCount() != null ? quiz.getVariantCount() : 1;
        Random rnd = new Random();

        for (int v = 1; v <= count; v++) {
            List<QuizQuestion> selected = new ArrayList<>(pool);
            if (Boolean.TRUE.equals(quiz.getShuffleQuestions())) {
                Collections.shuffle(selected, rnd);
            }
            selected = new ArrayList<>(selected.subList(0, perVariant));

            QuizVariant variant = QuizVariant.builder()
                    .quiz(quiz)
                    .variantNumber(v)
                    .build();

            int order = 0;
            for (QuizQuestion q : selected) {
                List<UUID> optionIds = q.getOptions().stream()
                        .sorted(Comparator.comparing(QuizOption::getSortOrder))
                        .map(QuizOption::getId)
                        .collect(Collectors.toList());
                if (Boolean.TRUE.equals(quiz.getShuffleOptions())) {
                    Collections.shuffle(optionIds, rnd);
                }
                variant.getQuestions().add(QuizVariantQuestion.builder()
                        .variant(variant)
                        .question(q)
                        .sortOrder(order++)
                        .optionOrder(toJson(optionIds))
                        .build());
            }
            quiz.getVariants().add(variant);
        }
    }

    @Transactional
    public QuizStartResponse startAttempt(UUID quizId, String userEmail) {
        Quiz quiz = findQuiz(quizId);
        User student = resolveUser(userEmail);
        validateQuizAccess(student, quiz, true);

        List<QuizVariant> variants = quizVariantRepository.findAllByQuiz_IdOrderByVariantNumberAsc(quizId);
        if (variants.isEmpty()) {
            List<QuizQuestion> ordered = new ArrayList<>(quiz.getQuestions());
            ordered.sort(Comparator.comparing(QuizQuestion::getSortOrder));
            List<QuizQuestionResponse> questions = new ArrayList<>();
            double total = 0.0;
            for (QuizQuestion q : ordered) {
                List<QuizOption> opts = q.getOptions().stream()
                        .sorted(Comparator.comparing(QuizOption::getSortOrder))
                        .collect(Collectors.toList());
                questions.add(QuizMapper.toStudentQuestion(q, q.getSortOrder(), opts));
                total += q.getPoints() != null ? q.getPoints() : 0.0;
            }
            return QuizStartResponse.builder()
                    .quizId(quiz.getId())
                    .title(quiz.getTitle())
                    .description(quiz.getDescription())
                    .timeLimitMinutes(quiz.getTimeLimitMinutes())
                    .questionCount(questions.size())
                    .totalPoints(round2(total))
                    .questions(questions)
                    .build();
        }

        QuizVariant picked = variants.get(new Random().nextInt(variants.size()));
        QuizVariant detail = quizVariantRepository.findWithDetailsById(picked.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Quiz variant not found"));

        List<QuizQuestionResponse> questions = new ArrayList<>();
        double total = 0.0;
        for (QuizVariantQuestion vq : detail.getQuestions()) {
            QuizQuestion q = vq.getQuestion();
            List<QuizOption> ordered = orderOptions(q, vq.getOptionOrder());
            questions.add(QuizMapper.toStudentQuestion(q, vq.getSortOrder(), ordered));
            total += q.getPoints() != null ? q.getPoints() : 0.0;
        }
        return QuizStartResponse.builder()
                .quizId(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .timeLimitMinutes(quiz.getTimeLimitMinutes())
                .variantId(detail.getId())
                .variantNumber(detail.getVariantNumber())
                .questionCount(questions.size())
                .totalPoints(round2(total))
                .questions(questions)
                .build();
    }

    private List<QuizOption> orderOptions(QuizQuestion question, String optionOrderJson) {
        Map<UUID, QuizOption> byId = question.getOptions().stream()
                .collect(Collectors.toMap(QuizOption::getId, o -> o, (a, b) -> a, LinkedHashMap::new));
        List<QuizOption> ordered = new ArrayList<>();
        if (optionOrderJson != null && !optionOrderJson.isBlank()) {
            try {
                String[] ids = objectMapper.readValue(optionOrderJson, String[].class);
                for (String id : ids) {
                    QuizOption o = byId.remove(UUID.fromString(id));
                    if (o != null) {
                        ordered.add(o);
                    }
                }
            } catch (Exception ignored) {
                // fall back to natural order below
            }
        }
        // Append any options not covered by the stored order (safety).
        ordered.addAll(byId.values());
        if (ordered.isEmpty()) {
            ordered = question.getOptions().stream()
                    .sorted(Comparator.comparing(QuizOption::getSortOrder))
                    .collect(Collectors.toList());
        }
        return ordered;
    }

    @Transactional
    public QuizResponse update(UUID id, QuizUpdateRequest request, String userEmail) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }

        Quiz quiz = findQuiz(id);
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, quiz.getSubject().getId());

        if (quiz.getStatus() == QuizStatus.CLOSED) {
            throw new BadRequestException("Closed quizzes cannot be updated");
        }

        validateQuestions(request.getQuestions());
        quiz.setTitle(request.getTitle().trim());
        quiz.setDescription(request.getDescription());
        quiz.setTimeLimitMinutes(request.getTimeLimitMinutes());
        // Variants reference the question pool; drop them before rebuilding questions.
        quiz.getVariants().clear();
        quiz.getQuestions().clear();
        applyQuestions(quiz, request.getQuestions(), false, false);
        quiz = quizRepository.save(quiz);
        return QuizMapper.toResponse(quiz, true, loadVariantSummaries(quiz.getId()));
    }

    @Transactional
    public QuizResponse publish(UUID id, String userEmail) {
        Quiz quiz = findQuiz(id);
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, quiz.getSubject().getId());

        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            throw new BadRequestException("Quiz must have at least one question before publishing");
        }

        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setPublishedAt(LocalDateTime.now());
        quiz = quizRepository.save(quiz);
        return QuizMapper.toResponse(quiz, true);
    }

    @Transactional
    public QuizResponse close(UUID id, String userEmail) {
        Quiz quiz = findQuiz(id);
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, quiz.getSubject().getId());
        quiz.setStatus(QuizStatus.CLOSED);
        quiz = quizRepository.save(quiz);
        return QuizMapper.toResponse(quiz, true);
    }

    @Transactional
    public QuizResponse toggleActive(UUID id, String userEmail) {
        Quiz quiz = findQuiz(id);
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, quiz.getSubject().getId());
        quiz.setActive(quiz.getActive() == null || !quiz.getActive());
        quiz = quizRepository.save(quiz);
        return QuizMapper.toResponse(quiz, true);
    }

    @Transactional
    public void delete(UUID id, String userEmail) {
        Quiz quiz = findQuiz(id);
        User lecturer = resolveUser(userEmail);
        subjectEnrollmentService.requireLecturerCanManageQuiz(lecturer, quiz.getSubject().getId());
        quizRepository.delete(quiz);
    }

    @Transactional
    public QuizAttemptResponse submit(UUID quizId, QuizSubmitRequest request, String userEmail) {
        Quiz quiz = findQuiz(quizId);
        User student = resolveUser(userEmail);
        subjectEnrollmentService.requireStudentCanAccessSubject(student, quiz.getSubject().getId());
        requireActiveSubscription(student);

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new BadRequestException("Quiz is not available for submission");
        }
        if (!Boolean.TRUE.equals(quiz.getActive())) {
            throw new BadRequestException("Quiz is not active");
        }

        List<QuizVariant> variants = quizVariantRepository.findAllByQuiz_IdOrderByVariantNumberAsc(quizId);
        QuizVariant variant = null;
        Map<UUID, QuizQuestion> questionMap;
        if (!variants.isEmpty()) {
            if (request.getVariantId() == null) {
                throw new BadRequestException("variantId is required for this quiz");
            }
            QuizVariant detail = quizVariantRepository.findWithDetailsById(request.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quiz variant not found"));
            if (detail.getQuiz() == null || !detail.getQuiz().getId().equals(quizId)) {
                throw new BadRequestException("Variant does not belong to this quiz");
            }
            variant = detail;
            questionMap = detail.getQuestions().stream()
                    .map(QuizVariantQuestion::getQuestion)
                    .collect(Collectors.toMap(QuizQuestion::getId, question -> question));
        } else {
            questionMap = quiz.getQuestions().stream()
                    .collect(Collectors.toMap(QuizQuestion::getId, question -> question));
        }

        if (request.getAnswers().size() != questionMap.size()) {
            throw new BadRequestException("All questions must be answered");
        }

        double totalScore = 0.0;
        double maxScore = questionMap.values().stream()
                .mapToDouble(q -> q.getPoints() != null ? q.getPoints() : 0.0)
                .sum();
        List<QuizAnswer> gradedAnswers = new ArrayList<>();

        for (QuizAnswerSubmitRequest answerRequest : request.getAnswers()) {
            QuizQuestion question = questionMap.get(answerRequest.getQuestionId());
            if (question == null) {
                throw new BadRequestException("Invalid question ID: " + answerRequest.getQuestionId());
            }

            GradingResult result = gradeAnswer(question, answerRequest);
            totalScore += result.scoreEarned();
            gradedAnswers.add(QuizAnswer.builder()
                    .question(question)
                    .isCorrect(result.isCorrect())
                    .scoreEarned(result.scoreEarned())
                    .answerPayload(result.payload())
                    .build());
        }

        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .student(student)
                .variant(variant)
                .totalScore(round2(totalScore))
                .maxScore(round2(maxScore))
                .build();
        for (QuizAnswer answer : gradedAnswers) {
            answer.setAttempt(attempt);
        }
        attempt.setAnswers(gradedAnswers);
        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);
        return QuizMapper.toAttemptResponse(savedAttempt, isResultsVisible(quiz));
    }

    private boolean isResultsVisible(Quiz quiz) {
        return !Boolean.FALSE.equals(quiz.getShowScore());
    }

    public List<QuizAttemptResponse> getMyAttempts(UUID quizId, String userEmail) {
        User student = resolveUser(userEmail);
        if (!RoleCodes.STUDENT.equals(student.getRole().getCode()) && !RoleCodes.ADMIN.equals(student.getRole().getCode())) {
            throw new BadRequestException("Only students can view their quiz attempts");
        }
        Quiz quiz = findQuiz(quizId);
        boolean resultsVisible = isResultsVisible(quiz);
        return quizAttemptRepository.findAllByQuiz_IdAndStudent_IdOrderBySubmittedAtDesc(quizId, student.getId())
                .stream()
                .map(attempt -> QuizMapper.toAttemptResponse(attempt, resultsVisible))
                .toList();
    }

    public QuizAttemptResponse getAttempt(UUID quizId, UUID attemptId, String userEmail) {
        User student = resolveUser(userEmail);
        QuizAttempt attempt = quizAttemptRepository.findByIdAndStudent_Id(attemptId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt not found"));
        if (!attempt.getQuiz().getId().equals(quizId)) {
            throw new BadRequestException("Attempt does not belong to this quiz");
        }
        return QuizMapper.toAttemptResponse(attempt, isResultsVisible(attempt.getQuiz()));
    }

    private Specification<Quiz> buildSpecification(QuizFilterRequest filter, User user) {
        String roleCode = user.getRole().getCode();
        Specification<Quiz> spec = Specification
                .where(QuizSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(QuizSpecifications.hasStatus(filter != null ? filter.getStatus() : null))
                .and(QuizSpecifications.keywordLike(filter != null ? filter.getKeyword() : null))
                .and(QuizSpecifications.hasSubjectId(filter != null ? filter.getSubjectId() : null));

        if (RoleCodes.STUDENT.equals(roleCode)) {
            List<UUID> subjectIds = subjectEnrollmentService.getStudentSubjectIds(user.getId());
            spec = spec.and(QuizSpecifications.subjectIdIn(subjectIds))
                    .and(QuizSpecifications.hasStatus(QuizStatus.PUBLISHED))
                    .and(QuizSpecifications.hasActive(true));
        } else if (RoleCodes.LECTURER.equals(roleCode)) {
            List<UUID> subjectIds = subjectEnrollmentService.getLecturerSubjectIds(user.getId());
            spec = spec.and(QuizSpecifications.subjectIdIn(subjectIds));
        }

        return spec;
    }

    private void validateQuizAccess(User user, Quiz quiz, boolean forStudentAttempt) {
        String roleCode = user.getRole().getCode();
        if (RoleCodes.ADMIN.equals(roleCode)) {
            return;
        }
        if (RoleCodes.LECTURER.equals(roleCode)) {
            subjectEnrollmentService.requireLecturerCanManageQuiz(user, quiz.getSubject().getId());
            return;
        }
        if (RoleCodes.STUDENT.equals(roleCode)) {
            subjectEnrollmentService.requireStudentCanAccessSubject(user, quiz.getSubject().getId());
            if (forStudentAttempt) {
                requireActiveSubscription(user);
                if (quiz.getStatus() != QuizStatus.PUBLISHED) {
                    throw new BadRequestException("Quiz is not published");
                }
                if (!Boolean.TRUE.equals(quiz.getActive())) {
                    throw new BadRequestException("Quiz is not active");
                }
            } else if (quiz.getStatus() != QuizStatus.PUBLISHED) {
                throw new BadRequestException("Quiz is not available");
            }
            return;
        }
        throw new BadRequestException("You do not have access to this quiz");
    }

    private void applyQuestions(Quiz quiz, List<QuizQuestionRequest> questionRequests,
                               boolean saveNewToBank, boolean aiGenerated) {
        for (QuizQuestionRequest questionRequest : questionRequests) {
            QuestionType questionType = questionTypeService.findActiveQuestionType(questionRequest.getQuestionTypeId());
            if (!QuestionTypeCodes.MULTIPLE_CHOICE.equals(questionType.getCode())) {
                throw new BadRequestException("Only multiple choice questions are supported");
            }

            QuizQuestion question = QuizQuestion.builder()
                    .quiz(quiz)
                    .questionType(questionType)
                    .multipleChoiceMode(questionRequest.getMultipleChoiceMode())
                    .questionText(questionRequest.getQuestionText().trim())
                    .points(questionRequest.getPoints())
                    .sortOrder(questionRequest.getSortOrder())
                    .sourceDocument(resolveSourceDocument(questionRequest.getSourceDocumentId(), quiz.getSubject()))
                    .sourceExcerpt(questionRequest.getSourceExcerpt())
                    .build();

            Set<QuizOption> options = questionRequest.getOptions().stream()
                    .map(optionRequest -> QuizOption.builder()
                            .question(question)
                            .optionText(optionRequest.getOptionText().trim())
                            .isCorrect(optionRequest.getIsCorrect())
                            .sortOrder(optionRequest.getSortOrder())
                            .build())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            question.setOptions(options);

            // Đồng bộ ngân hàng câu hỏi: gắn câu gốc nếu có, hoặc lưu mới khi tạo quiz.
            if (questionRequest.getBankQuestionId() != null) {
                question.setBankQuestion(
                        bankQuestionService.requireForSubject(questionRequest.getBankQuestionId(), quiz.getSubject().getId()));
            } else if (saveNewToBank) {
                question.setBankQuestion(
                        bankQuestionService.saveFromQuizQuestion(question, quiz.getSubject(), quiz.getCreatedBy(), aiGenerated));
            }

            quiz.getQuestions().add(question);
        }
    }

    private void validateQuestions(List<QuizQuestionRequest> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BadRequestException("Quiz must have at least one question");
        }

        for (int i = 0; i < questions.size(); i++) {
            QuizQuestionRequest question = questions.get(i);
            if (question == null) {
                throw new BadRequestException("Question at index " + i + " must not be null");
            }
            String prefix = "Question at index " + i + ": ";

            if (question.getQuestionTypeId() == null) {
                throw new BadRequestException(prefix + "questionTypeId is required");
            }
            if (question.getQuestionText() == null || question.getQuestionText().isBlank()) {
                throw new BadRequestException(prefix + "questionText is required");
            }
            if (question.getPoints() == null) {
                throw new BadRequestException(prefix + "points is required");
            }
            if (question.getPoints() < 0.1) {
                throw new BadRequestException(prefix + "points must be at least 0.1");
            }
            if (question.getSortOrder() == null) {
                throw new BadRequestException(prefix + "sortOrder is required");
            }
            if (question.getSortOrder() < 0) {
                throw new BadRequestException(prefix + "sortOrder must be >= 0");
            }
            if (question.getMultipleChoiceMode() == null) {
                throw new BadRequestException(prefix + "multipleChoiceMode is required");
            }

            QuestionType questionType = questionTypeService.findActiveQuestionType(question.getQuestionTypeId());
            if (!QuestionTypeCodes.MULTIPLE_CHOICE.equals(questionType.getCode())) {
                throw new BadRequestException(prefix + "only multiple choice questions are supported");
            }
            validateMultipleChoiceQuestion(question, prefix);
        }
    }

    private void validateMultipleChoiceQuestion(QuizQuestionRequest question, String prefix) {
        List<QuizOptionRequest> options = question.getOptions();
        if (options == null || options.isEmpty()) {
            throw new BadRequestException(prefix + "options are required");
        }

        long correctCount = 0;
        for (int j = 0; j < options.size(); j++) {
            QuizOptionRequest option = options.get(j);
            if (option == null) {
                throw new BadRequestException(prefix + "option at index " + j + " must not be null");
            }
            if (option.getOptionText() == null || option.getOptionText().isBlank()) {
                throw new BadRequestException(prefix + "optionText is required for option at index " + j);
            }
            if (option.getIsCorrect() == null) {
                throw new BadRequestException(prefix + "isCorrect is required for option at index " + j);
            }
            if (option.getSortOrder() == null) {
                throw new BadRequestException(prefix + "sortOrder is required for option at index " + j);
            }
            if (Boolean.TRUE.equals(option.getIsCorrect())) {
                correctCount++;
            }
        }

        switch (question.getMultipleChoiceMode()) {
            case SINGLE -> {
                if (options.size() < 2) {
                    throw new BadRequestException(prefix + "single choice questions must have at least 2 options");
                }
                if (correctCount != 1) {
                    throw new BadRequestException(prefix + "single choice questions must have exactly 1 correct answer");
                }
            }
            case MULTIPLE -> {
                if (options.size() < 2) {
                    throw new BadRequestException(prefix + "multiple select questions must have at least 2 options");
                }
                if (correctCount < 1) {
                    throw new BadRequestException(prefix + "multiple select questions must have at least 1 correct answer");
                }
            }
        }
    }

    private GradingResult gradeAnswer(QuizQuestion question, QuizAnswerSubmitRequest answerRequest) {
        return gradeMultipleChoice(question, answerRequest);
    }

    private GradingResult gradeMultipleChoice(QuizQuestion question, QuizAnswerSubmitRequest answerRequest) {
        List<UUID> selected = answerRequest.getSelectedOptionIds();
        if (selected == null || selected.isEmpty()) {
            throw new BadRequestException("Selected options are required for multiple choice question");
        }
        for (int i = 0; i < selected.size(); i++) {
            if (selected.get(i) == null) {
                throw new BadRequestException("selectedOptionIds[" + i + "] must not be null");
            }
        }

        Set<UUID> selectedIds = new HashSet<>(selected);
        Set<UUID> correctIds = question.getOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getIsCorrect()))
                .map(QuizOption::getId)
                .collect(Collectors.toSet());

        boolean isCorrect = selectedIds.equals(correctIds);
        double scoreEarned = isCorrect ? (question.getPoints() != null ? question.getPoints() : 0.0) : 0.0;
        return new GradingResult(isCorrect, scoreEarned, toJson(Map.of("selectedOptionIds", selected)));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Quiz findQuiz(UUID id) {
        return quizRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
    }

    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("Authenticated user is required");
        }
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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

    private void requireActiveSubscription(User student) {
        if (RoleCodes.ADMIN.equals(student.getRole().getCode())) {
            return;
        }
        boolean hasActive = userSubscriptionRepository
                .findActiveSubscription(student.getId(), LocalDateTime.now())
                .isPresent();
        if (!hasActive) {
            throw new BadRequestException("You need an active subscription to take this quiz");
        }
    }

    private record GradingResult(boolean isCorrect, double scoreEarned, String payload) {
    }
}
