package swdchatbox.system.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.subscription.entity.StudentSubscription;
import swdchatbox.system.subscription.entity.SubscriptionPlan;
import swdchatbox.system.subscription.repository.StudentSubscriptionRepository;
import swdchatbox.system.subscription.repository.SubscriptionPlanRepository;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;
import swdchatbox.system.wallet.service.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionPurchaseService {

    private final StudentSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    @Transactional
    public StudentSubscription purchase(UUID planId, String email, BigDecimal price) {

        User student = findStudentByEmail(email);

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        walletService.debitWallet(
                email,
                price,
                "SUB-" + planId,
                "Subscription purchase for plan " + plan.getName()
        );

        StudentSubscription subscription = subscriptionRepository
                .findByStudent_IdAndSubscriptionPlan_Id(student.getId(), planId)
                .orElse(
                        StudentSubscription.builder()
                                .student(student)
                                .subscriptionPlan(plan)
                                .build()
                );

        subscription.setActive(true);
        subscription.setSubscribedAt(LocalDateTime.now());

        if (plan.getDurationInMonths() != null) {
            subscription.setExpiresAt(
                    LocalDateTime.now().plusMonths(plan.getDurationInMonths())
            );
        }

        subscription.setUnsubscribedAt(null);

        return subscriptionRepository.save(subscription);
    }

    private User findStudentByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            throw new BadRequestException("Only student accounts can buy subscriptions");
        }

        return user;
    }
}