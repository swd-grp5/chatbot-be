package swdchatbox.modules.subscription.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;
import swdchatbox.modules.subscription.repository.SubscriptionPlanRepository;

import java.math.BigDecimal;

@Component
@Order(5)
@RequiredArgsConstructor
public class DefaultSubscriptionPlanInitializer implements CommandLineRunner {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedPlan("Free", BigDecimal.ZERO, 10, 999, "Gói miễn phí mặc định");
        seedPlan("Basic", new BigDecimal("99000"), 50, 1, "Gói Basic - 50 câu hỏi/ngày");
        seedPlan("Premium", new BigDecimal("199000"), 200, 1, "Gói Premium - 200 câu hỏi/ngày");
    }

    private void seedPlan(String name, BigDecimal price, int dailyQuestionLimit, int durationInMonths, String description) {
        if (subscriptionPlanRepository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(name)
                .price(price)
                .dailyQuestionLimit(dailyQuestionLimit)
                .durationInMonths(durationInMonths)
                .description(description)
                .active(true)
                .build();

        subscriptionPlanRepository.save(plan);
    }
}
