package swdchatbox.modules.quiz.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.quiz.QuestionTypeCodes;
import swdchatbox.modules.quiz.entity.QuestionType;
import swdchatbox.modules.quiz.repository.QuestionTypeRepository;

@Component
@Order(2)
@RequiredArgsConstructor
public class DefaultQuestionTypeInitializer implements CommandLineRunner {

    private final QuestionTypeRepository questionTypeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seed(QuestionTypeCodes.MULTIPLE_CHOICE, "Trắc nghiệm",
                "Chọn 1 hoặc nhiều đáp án đúng. Biến thể: single choice, multiple select.", 0);
    }

    private void seed(String code, String name, String description, int sortOrder) {
        if (questionTypeRepository.existsByCode(code)) return;
        questionTypeRepository.save(QuestionType.builder()
                .code(code).name(name).description(description)
                .sortOrder(sortOrder).active(true).build());
    }
}
