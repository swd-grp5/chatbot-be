package swdchatbox.modules.credit.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.credit.entity.CreditFeatureCost;
import swdchatbox.modules.credit.repository.CreditFeatureCostRepository;

@Component
@Order(3)
@RequiredArgsConstructor
public class DefaultCreditFeatureCostInitializer implements CommandLineRunner {

    private final CreditFeatureCostRepository featureCostRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedCost("CHAT_QUESTION", 1);
        seedCost("QUIZ_GENERATE", 5);
        seedCost("PDF_SUMMARY", 10);
        seedCost("DOC_ANALYSIS", 20);
    }

    private void seedCost(String featureName, int cost) {
        if (featureCostRepository.findByFeatureName(featureName).isPresent()) {
            return;
        }

        CreditFeatureCost featureCost = CreditFeatureCost.builder()
                .featureName(featureName)
                .creditCost(cost)
                .build();

        featureCostRepository.save(featureCost);
    }
}
