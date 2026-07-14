package swdchatbox.modules.credit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.credit.entity.CreditFeatureCost;
import swdchatbox.modules.credit.entity.CreditUsageLog;
import swdchatbox.modules.credit.entity.UserCreditAccount;
import swdchatbox.modules.credit.repository.CreditFeatureCostRepository;
import swdchatbox.modules.credit.repository.CreditUsageLogRepository;
import swdchatbox.modules.credit.repository.UserCreditAccountRepository;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;
import swdchatbox.modules.subscription.entity.UserSubscription;
import swdchatbox.modules.subscription.repository.SubscriptionPlanRepository;
import swdchatbox.modules.subscription.repository.UserSubscriptionRepository;
import swdchatbox.modules.user.entity.User;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserCreditAccountRepository creditAccountRepository;
    private final CreditFeatureCostRepository featureCostRepository;
    private final CreditUsageLogRepository usageLogRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional
    public UserCreditAccount getOrCreateAccount(User user) {
        return creditAccountRepository.findByUser_Id(user.getId())
                .map(account -> {
                    lazyResetIfNeeded(account);
                    return account;
                })
                .orElseGet(() -> {
                    SubscriptionPlan plan = getActiveOrDefaultPlan(user.getId());
                    LocalDateTime now = LocalDateTime.now(VN_ZONE);
                    LocalDateTime nextReset = calculateNextReset(now, plan.getResetPeriod());
                    UserCreditAccount newAccount = UserCreditAccount.builder()
                            .user(user)
                            .remainingCredits(plan.getCreditAmount())
                            .periodStartedAt(now)
                            .nextResetAt(nextReset)
                            .build();
                    return creditAccountRepository.save(newAccount);
                });
    }

    @Transactional
    public void grantForPlan(User user, SubscriptionPlan plan) {
        UserCreditAccount account = creditAccountRepository.findByUser_Id(user.getId())
                .orElseGet(() -> UserCreditAccount.builder().user(user).build());

        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        LocalDateTime nextReset = calculateNextReset(now, plan.getResetPeriod());

        account.setRemainingCredits(plan.getCreditAmount());
        account.setPeriodStartedAt(now);
        account.setNextResetAt(nextReset);

        creditAccountRepository.save(account);
        log.info("Granted {} credits to user {} for plan {}", plan.getCreditAmount(), user.getEmail(), plan.getName());
    }

    @Transactional
    public void ensureEnough(User user, String featureName) {
        if (!swdchatbox.modules.role.RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            return;
        }

        CreditFeatureCost costConfig = featureCostRepository.findByFeatureName(featureName)
                .orElseThrow(() -> new ResourceNotFoundException("Credit cost configuration not found for feature: " + featureName));

        UserCreditAccount account = getOrCreateAccount(user);

        if (account.getRemainingCredits() < costConfig.getCreditCost()) {
            throw new BadRequestException("Bạn không đủ credit để sử dụng tính năng này. Yêu cầu: " 
                    + costConfig.getCreditCost() + ", Hiện có: " + account.getRemainingCredits());
        }
    }

    @Transactional
    public void consume(User user, String featureName) {
        if (!swdchatbox.modules.role.RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            return;
        }

        CreditFeatureCost costConfig = featureCostRepository.findByFeatureName(featureName)
                .orElseThrow(() -> new ResourceNotFoundException("Credit cost configuration not found for feature: " + featureName));

        UserCreditAccount account = getOrCreateAccount(user);

        int cost = costConfig.getCreditCost();
        if (account.getRemainingCredits() < cost) {
            throw new BadRequestException("Không đủ credit. Yêu cầu: " + cost + ", Hiện có: " + account.getRemainingCredits());
        }

        account.setRemainingCredits(account.getRemainingCredits() - cost);
        creditAccountRepository.save(account);

        usageLogRepository.save(CreditUsageLog.builder()
                .user(user)
                .featureName(featureName)
                .creditsUsed(cost)
                .build());

        log.info("User {} consumed {} credits for feature {}", user.getEmail(), cost, featureName);
    }

    private void lazyResetIfNeeded(UserCreditAccount account) {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        if (now.isAfter(account.getNextResetAt())) {
            SubscriptionPlan plan = getActiveOrDefaultPlan(account.getUser().getId());
            
            // Loop in case user hasn't logged in for multiple reset cycles
            LocalDateTime tempNextReset = account.getNextResetAt();
            LocalDateTime tempStart = account.getPeriodStartedAt();
            while (now.isAfter(tempNextReset)) {
                tempStart = tempNextReset;
                tempNextReset = calculateNextReset(tempStart, plan.getResetPeriod());
            }

            account.setRemainingCredits(plan.getCreditAmount());
            account.setPeriodStartedAt(tempStart);
            account.setNextResetAt(tempNextReset);
            creditAccountRepository.save(account);
            log.info("Lazy reset credit account for user {} to {}", account.getUser().getEmail(), plan.getCreditAmount());
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cronBatchReset() {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        List<UserCreditAccount> expiredAccounts = creditAccountRepository.findAllExpiredAccounts(now);
        log.info("Cron batch reset starting. Found {} expired accounts.", expiredAccounts.size());
        
        for (UserCreditAccount account : expiredAccounts) {
            try {
                SubscriptionPlan plan = getActiveOrDefaultPlan(account.getUser().getId());
                
                LocalDateTime tempNextReset = account.getNextResetAt();
                LocalDateTime tempStart = account.getPeriodStartedAt();
                while (now.isAfter(tempNextReset)) {
                    tempStart = tempNextReset;
                    tempNextReset = calculateNextReset(tempStart, plan.getResetPeriod());
                }

                account.setRemainingCredits(plan.getCreditAmount());
                account.setPeriodStartedAt(tempStart);
                account.setNextResetAt(tempNextReset);
                creditAccountRepository.save(account);
            } catch (Exception e) {
                log.error("Failed to reset credit account ID {}", account.getId(), e);
            }
        }
        log.info("Cron batch reset completed.");
    }

    private SubscriptionPlan getActiveOrDefaultPlan(UUID userId) {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        return subscriptionRepository.findActiveSubscription(userId, now)
                .map(UserSubscription::getSubscriptionPlan)
                .orElseGet(() -> subscriptionPlanRepository.findByNameIgnoreCase("Free")
                        .orElseGet(() -> SubscriptionPlan.builder()
                                .name("Free")
                                .price(BigDecimal.ZERO)
                                .creditAmount(100)
                                .resetPeriod(swdchatbox.modules.subscription.enums.ResetPeriod.DAILY)
                                .durationValue(999)
                                .durationUnit(swdchatbox.modules.subscription.enums.DurationUnit.MONTH)
                                .description("Gói miễn phí mặc định")
                                .active(true)
                                .build()
                        )
                );
    }

    private LocalDateTime calculateNextReset(LocalDateTime start, swdchatbox.modules.subscription.enums.ResetPeriod period) {
        if (period == null) {
            return start.plusDays(1);
        }
        return switch (period) {
            case HOURLY -> start.plusHours(1);
            case DAILY -> start.plusDays(1);
            case MONTHLY -> start.plusMonths(1);
        };
    }
}
