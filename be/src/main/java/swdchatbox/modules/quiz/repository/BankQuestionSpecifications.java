package swdchatbox.modules.quiz.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.modules.quiz.entity.BankQuestion;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;

import java.util.Collection;
import java.util.UUID;

public final class BankQuestionSpecifications {

    private BankQuestionSpecifications() {
    }

    public static Specification<BankQuestion> hasSubjectId(UUID subjectId) {
        return (root, query, cb) -> subjectId == null ? cb.conjunction()
                : cb.equal(root.get("subject").get("id"), subjectId);
    }

    public static Specification<BankQuestion> subjectIdIn(Collection<UUID> subjectIds) {
        return (root, query, cb) -> {
            if (subjectIds == null) return cb.conjunction();
            if (subjectIds.isEmpty()) return cb.disjunction();
            return root.get("subject").get("id").in(subjectIds);
        };
    }

    public static Specification<BankQuestion> hasQuestionTypeId(UUID questionTypeId) {
        return (root, query, cb) -> questionTypeId == null ? cb.conjunction()
                : cb.equal(root.get("questionType").get("id"), questionTypeId);
    }

    public static Specification<BankQuestion> hasMode(MultipleChoiceMode mode) {
        return (root, query, cb) -> mode == null ? cb.conjunction()
                : cb.equal(root.get("multipleChoiceMode"), mode);
    }

    public static Specification<BankQuestion> hasActive(Boolean active) {
        return (root, query, cb) -> active == null ? cb.conjunction()
                : cb.equal(root.get("active"), active);
    }

    public static Specification<BankQuestion> hasAiGenerated(Boolean aiGenerated) {
        return (root, query, cb) -> aiGenerated == null ? cb.conjunction()
                : cb.equal(root.get("aiGenerated"), aiGenerated);
    }

    public static Specification<BankQuestion> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return cb.conjunction();
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("questionText")), like);
        };
    }
}
