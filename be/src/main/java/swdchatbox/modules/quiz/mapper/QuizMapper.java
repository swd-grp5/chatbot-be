package swdchatbox.modules.quiz.mapper;

import swdchatbox.modules.quiz.QuestionTypeCodes;
import swdchatbox.modules.quiz.dto.response.*;
import swdchatbox.modules.quiz.entity.*;

import java.util.List;

public final class QuizMapper {

    private QuizMapper() {
    }

    public static QuizSummaryResponse toSummary(Quiz quiz) {
        return QuizSummaryResponse.builder()
                .id(quiz.getId())
                .subjectId(quiz.getSubject() != null ? quiz.getSubject().getId() : null)
                .subjectCode(quiz.getSubject() != null ? quiz.getSubject().getCode() : null)
                .subjectName(quiz.getSubject() != null ? quiz.getSubject().getName() : null)
                .title(quiz.getTitle())
                .status(quiz.getStatus())
                .timeLimitMinutes(quiz.getTimeLimitMinutes())
                .totalPoints(sumPoints(quiz))
                .questionCount(quiz.getQuestions() != null ? quiz.getQuestions().size() : 0)
                .active(quiz.getActive())
                .aiGenerated(quiz.getAiGenerated())
                .showScore(quiz.getShowScore())
                .variantCount(quiz.getVariantCount())
                .publishedAt(quiz.getPublishedAt())
                .createdAt(quiz.getCreatedAt())
                .build();
    }

    public static QuizResponse toResponse(Quiz quiz, boolean includeAnswers) {
        return toResponse(quiz, includeAnswers, null);
    }

    public static QuizResponse toResponse(Quiz quiz, boolean includeAnswers,
                                          List<QuizVariantSummaryResponse> variants) {
        return QuizResponse.builder()
                .id(quiz.getId())
                .subjectId(quiz.getSubject() != null ? quiz.getSubject().getId() : null)
                .subjectCode(quiz.getSubject() != null ? quiz.getSubject().getCode() : null)
                .subjectName(quiz.getSubject() != null ? quiz.getSubject().getName() : null)
                .createdById(quiz.getCreatedBy() != null ? quiz.getCreatedBy().getId() : null)
                .createdByName(quiz.getCreatedBy() != null ? quiz.getCreatedBy().getFullName() : null)
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
                .timeLimitMinutes(quiz.getTimeLimitMinutes())
                .totalPoints(sumPoints(quiz))
                .questionCount(quiz.getQuestions() != null ? quiz.getQuestions().size() : 0)
                .active(quiz.getActive())
                .aiGenerated(quiz.getAiGenerated())
                .shuffleQuestions(quiz.getShuffleQuestions())
                .shuffleOptions(quiz.getShuffleOptions())
                .showScore(quiz.getShowScore())
                .questionsPerVariant(quiz.getQuestionsPerVariant())
                .variantCount(quiz.getVariantCount())
                .publishedAt(quiz.getPublishedAt())
                .createdAt(quiz.getCreatedAt())
                .updatedAt(quiz.getUpdatedAt())
                .questions(toQuestions(quiz.getQuestions(), includeAnswers))
                .variants(variants)
                .build();
    }

    public static QuizAttemptResponse toAttemptResponse(QuizAttempt attempt) {
        return toAttemptResponse(attempt, true);
    }

    public static QuizAttemptResponse toAttemptResponse(QuizAttempt attempt, boolean resultsVisible) {
        QuizVariant variant = attempt.getVariant();
        QuizAttemptResponse.QuizAttemptResponseBuilder builder = QuizAttemptResponse.builder()
                .id(attempt.getId())
                .quizId(attempt.getQuiz().getId())
                .quizTitle(attempt.getQuiz().getTitle())
                .variantId(variant != null ? variant.getId() : null)
                .variantNumber(variant != null ? variant.getVariantNumber() : null)
                .resultsVisible(resultsVisible)
                .submittedAt(attempt.getSubmittedAt());

        if (!resultsVisible) {
            return builder.answers(List.of()).build();
        }

        double max = attempt.getMaxScore() != null ? attempt.getMaxScore() : 0.0;
        double total = attempt.getTotalScore() != null ? attempt.getTotalScore() : 0.0;
        double pct = max == 0.0 ? 0.0 : (total * 100.0) / max;
        return builder
                .totalScore(attempt.getTotalScore())
                .maxScore(attempt.getMaxScore())
                .percentage(Math.round(pct * 100.0) / 100.0)
                .answers(attempt.getAnswers().stream().map(QuizMapper::toAnswerResult).toList())
                .build();
    }

    private static QuizAnswerResultResponse toAnswerResult(QuizAnswer answer) {
        QuizQuestion q = answer.getQuestion();
        return QuizAnswerResultResponse.builder()
                .questionId(q.getId())
                .questionType(QuestionTypeMapper.toResponse(q.getQuestionType()))
                .questionText(q.getQuestionText())
                .isCorrect(answer.getIsCorrect())
                .scoreEarned(answer.getScoreEarned())
                .maxScore(q.getPoints())
                .build();
    }

    private static List<QuizQuestionResponse> toQuestions(List<QuizQuestion> questions, boolean includeAnswers) {
        if (questions == null || questions.isEmpty()) return List.of();
        return questions.stream().map(q -> toQuestion(q, includeAnswers)).toList();
    }

    private static QuizQuestionResponse toQuestion(QuizQuestion question, boolean includeAnswers) {
        return QuizQuestionResponse.builder()
                .id(question.getId())
                .questionType(QuestionTypeMapper.toResponse(question.getQuestionType()))
                .multipleChoiceMode(question.getMultipleChoiceMode())
                .questionText(question.getQuestionText())
                .points(question.getPoints())
                .sortOrder(question.getSortOrder())
                .sourceDocumentId(includeAnswers && question.getSourceDocument() != null ? question.getSourceDocument().getId() : null)
                .sourceDocumentTitle(includeAnswers && question.getSourceDocument() != null ? question.getSourceDocument().getTitle() : null)
                .sourceExcerpt(includeAnswers ? question.getSourceExcerpt() : null)
                .options(question.getOptions().stream()
                        .map(o -> QuizOptionResponse.builder()
                                .id(o.getId())
                                .optionText(o.getOptionText())
                                .isCorrect(includeAnswers ? o.getIsCorrect() : null)
                                .sortOrder(o.getSortOrder())
                                .build())
                        .toList())
                .build();
    }

    public static QuizVariantSummaryResponse toVariantSummary(QuizVariant variant) {
        double total = variant.getQuestions().stream()
                .map(QuizVariantQuestion::getQuestion)
                .mapToDouble(q -> q != null && q.getPoints() != null ? q.getPoints() : 0.0)
                .sum();
        return QuizVariantSummaryResponse.builder()
                .id(variant.getId())
                .variantNumber(variant.getVariantNumber())
                .questionCount(variant.getQuestions().size())
                .totalPoints(Math.round(total * 100.0) / 100.0)
                .build();
    }

    /** Câu hỏi cho sinh viên làm bài: ẩn đáp án đúng, đáp án theo thứ tự đã cho (đã xáo trộn). */
    public static QuizQuestionResponse toStudentQuestion(QuizQuestion question, int sortOrder,
                                                         List<QuizOption> orderedOptions) {
        List<QuizOptionResponse> options = new java.util.ArrayList<>();
        for (int i = 0; i < orderedOptions.size(); i++) {
            QuizOption o = orderedOptions.get(i);
            options.add(QuizOptionResponse.builder()
                    .id(o.getId())
                    .optionText(o.getOptionText())
                    .isCorrect(null)
                    .sortOrder(i)
                    .build());
        }
        return QuizQuestionResponse.builder()
                .id(question.getId())
                .questionType(QuestionTypeMapper.toResponse(question.getQuestionType()))
                .multipleChoiceMode(question.getMultipleChoiceMode())
                .questionText(question.getQuestionText())
                .points(question.getPoints())
                .sortOrder(sortOrder)
                .options(options)
                .build();
    }

    private static double sumPoints(Quiz quiz) {
        if (quiz.getQuestions() == null) return 0.0;
        return quiz.getQuestions().stream()
                .mapToDouble(q -> q.getPoints() != null ? q.getPoints() : 0.0)
                .sum();
    }
}
