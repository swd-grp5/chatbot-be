package swdchatbox.modules.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.invoice.service.InvoiceService;
import swdchatbox.modules.subscription.dto.response.UserSubscriptionResponse;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;
import swdchatbox.modules.subscription.entity.UserSubscription;
import swdchatbox.modules.subscription.repository.SubscriptionPlanRepository;
import swdchatbox.modules.subscription.repository.UserSubscriptionRepository;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;
import swdchatbox.modules.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSubscriptionService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final swdchatbox.modules.wallet.service.WalletService walletService;
    private final SubscriptionPurchaseService subscriptionPurchaseService;
    private final swdchatbox.modules.credit.service.CreditService creditService;

    @Transactional
    public UserSubscriptionResponse subscribe(UUID planId, String email) {
        User user = findEligibleUserByEmail(email);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new BadRequestException("This subscription plan is currently inactive");
        }

        // Check wallet balance if paid plan
        if (plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            Wallet wallet = walletService.getOrCreateWallet(email);
            if (wallet.getBalance().compareTo(plan.getPrice()) < 0) {
                throw new BadRequestException("Số dư ví không đủ để mua gói cước này");
            }
        }

        UserSubscription activeSub = subscriptionRepository
                .findActiveSubscription(user.getId(), LocalDateTime.now())
                .orElse(null);

        if (activeSub != null) {
            if (activeSub.getSubscriptionPlan().getId().equals(planId)) {
                throw new BadRequestException("You already have an active subscription to this plan");
            }
            activeSub.setActive(false);
            activeSub.setUnsubscribedAt(LocalDateTime.now());
            subscriptionRepository.save(activeSub);
        }

        UserSubscription saved;
        if (plan.getPrice() == null || plan.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            LocalDateTime subscribedAt = LocalDateTime.now();
            LocalDateTime expiresAt = calculateExpirationDate(subscribedAt, plan.getDurationValue(), plan.getDurationUnit());

            UserSubscription subscription = UserSubscription.builder()
                    .user(user)
                    .subscriptionPlan(plan)
                    .active(true)
                    .subscribedAt(subscribedAt)
                    .expiresAt(expiresAt)
                    .build();

            saved = subscriptionRepository.save(subscription);
            swdchatbox.modules.invoice.entity.Invoice invoice = invoiceService.createSubscriptionInvoice(saved, BigDecimal.ZERO);
            invoiceService.markPaid(invoice);
        } else {
            saved = subscriptionPurchaseService.purchase(planId, email, plan.getPrice());
        }

        creditService.grantForPlan(user, plan);

        return toResponse(saved);
    }

    @Transactional
    public void unsubscribe(String email) {
        User user = findEligibleUserByEmail(email);

        UserSubscription subscription = subscriptionRepository
                .findActiveSubscription(user.getId(), LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found to unsubscribe"));

        subscription.setActive(false);
        subscription.setUnsubscribedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        // Downgrade back to Free plan & refill Free credit
        SubscriptionPlan freePlan = subscriptionPlanRepository.findByNameIgnoreCase("Free")
                .orElseThrow(() -> new ResourceNotFoundException("Default Free subscription plan not found"));
        creditService.grantForPlan(user, freePlan);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getCurrentUserPlan(String email) {
        User user = findEligibleUserByEmail(email);

        return subscriptionRepository.findActiveSubscription(user.getId(), LocalDateTime.now())
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

    @Transactional
    public swdchatbox.modules.subscription.dto.response.CurrentUserSubscriptionResponse getCurrentUserSubscriptionDetails(String email) {
        User user = findEligibleUserByEmail(email);
        SubscriptionPlan plan = getCurrentUserPlan(email);
        swdchatbox.modules.credit.entity.UserCreditAccount account = creditService.getOrCreateAccount(user);

        return swdchatbox.modules.subscription.dto.response.CurrentUserSubscriptionResponse.builder()
                .plan(plan)
                .remainingCredits(account.getRemainingCredits())
                .nextResetAt(account.getNextResetAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        return subscriptionPlanRepository.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionPlan> getAllPlansForAdmin(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return subscriptionPlanRepository.findAll(pageable);
    }

    @Transactional
    public SubscriptionPlan createPlan(SubscriptionPlan plan) {
        if (subscriptionPlanRepository.findByNameIgnoreCase(plan.getName()).isPresent()) {
            throw new BadRequestException("Subscription plan name already exists");
        }
        return subscriptionPlanRepository.save(plan);
    }

    @Transactional
    public SubscriptionPlan updatePlan(UUID id, SubscriptionPlan updatedPlan) {
        SubscriptionPlan existingPlan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        existingPlan.setName(updatedPlan.getName());
        existingPlan.setPrice(updatedPlan.getPrice());
        existingPlan.setCreditAmount(updatedPlan.getCreditAmount());
        existingPlan.setResetPeriod(updatedPlan.getResetPeriod());
        existingPlan.setDurationValue(updatedPlan.getDurationValue());
        existingPlan.setDurationUnit(updatedPlan.getDurationUnit());
        existingPlan.setDescription(updatedPlan.getDescription());
        existingPlan.setActive(updatedPlan.getActive());

        return subscriptionPlanRepository.save(existingPlan);
    }

    @Transactional
    public void deletePlan(UUID id) {
        if (!subscriptionPlanRepository.existsById(id)) {
            throw new ResourceNotFoundException("Subscription plan not found");
        }
        subscriptionPlanRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<UserSubscriptionResponse> findMySubscriptionHistory(String email) {
        User user = findEligibleUserByEmail(email);
        List<UserSubscription> subscriptions = subscriptionRepository
                .findAllByUser_IdOrderBySubscribedAtDesc(user.getId());
        return subscriptions.stream().map(this::toResponse).toList();
    }

    private User findEligibleUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!swdchatbox.modules.role.RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            throw new BadRequestException("Only student accounts can use subscriptions");
        }

        return user;
    }

    private LocalDateTime calculateExpirationDate(LocalDateTime subscribedAt, int durationValue, swdchatbox.modules.subscription.enums.DurationUnit durationUnit) {
        if (durationUnit == swdchatbox.modules.subscription.enums.DurationUnit.DAY) {
            return subscribedAt.plusDays(durationValue);
        } else {
            return subscribedAt.plusMonths(durationValue);
        }
    }

    private UserSubscriptionResponse toResponse(UserSubscription subscription) {
        return UserSubscriptionResponse.builder()
                .id(subscription.getId())
                .planId(subscription.getSubscriptionPlan().getId())
                .planName(subscription.getSubscriptionPlan().getName())
                .active(subscription.getActive())
                .subscribedAt(subscription.getSubscribedAt())
                .expiresAt(subscription.getExpiresAt())
                .unsubscribedAt(subscription.getUnsubscribedAt())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }
}
