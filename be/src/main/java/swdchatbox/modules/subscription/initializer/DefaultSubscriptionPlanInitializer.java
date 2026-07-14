package swdchatbox.modules.subscription.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;
import swdchatbox.modules.subscription.enums.DurationUnit;
import swdchatbox.modules.subscription.enums.ResetPeriod;
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
        seedPlan("Free", BigDecimal.ZERO, 100, ResetPeriod.DAILY, 999, DurationUnit.MONTH, "Gói miễn phí mặc định");
        seedPlan("Basic", new BigDecimal("99000"), 500, ResetPeriod.MONTHLY, 1, DurationUnit.MONTH, "Gói Basic - 500 credits/tháng");
        seedPlan("Pro", new BigDecimal("199000"), 1500, ResetPeriod.MONTHLY, 1, DurationUnit.MONTH, "Gói Pro - 1500 credits/tháng");
        seedPlan("Ultra", new BigDecimal("399000"), 5000, ResetPeriod.MONTHLY, 1, DurationUnit.MONTH, "Gói Ultra - 5000 credits/tháng");
    }

    private void seedPlan(String name, BigDecimal price, int creditAmount, ResetPeriod resetPeriod, 
                          int durationValue, DurationUnit durationUnit, String description) {
        if (subscriptionPlanRepository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(name)
                .price(price)
                .creditAmount(creditAmount)
                .resetPeriod(resetPeriod)
                .durationValue(durationValue)
                .durationUnit(durationUnit)
                .description(description)
                .active(true)
                .build();

        subscriptionPlanRepository.save(plan);
    }
}
