package swdchatbox.system.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.invoice.service.InvoiceService;
import swdchatbox.system.subscription.dto.response.UserSubscriptionResponse;
import swdchatbox.system.subscription.entity.SubscriptionPlan;
import swdchatbox.system.subscription.entity.UserSubscription;
import swdchatbox.system.subscription.repository.SubscriptionPlanRepository;
import swdchatbox.system.subscription.repository.UserSubscriptionRepository;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

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

    @Transactional
    public UserSubscriptionResponse subscribe(UUID planId, String email) {
        User user = findEligibleUserByEmail(email);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new BadRequestException("This subscription plan is currently inactive");
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

        LocalDateTime subscribedAt = LocalDateTime.now();
        LocalDateTime expiresAt = subscribedAt.plusMonths(plan.getDurationInMonths());

        UserSubscription subscription = UserSubscription.builder()
                .user(user)
                .subscriptionPlan(plan)
                .active(true)
                .subscribedAt(subscribedAt)
                .expiresAt(expiresAt)
                .build();

        UserSubscription saved = subscriptionRepository.save(subscription);
        invoiceService.createSubscriptionInvoice(saved);
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
                                .dailyQuestionLimit(10)
                                .durationInMonths(999)
                                .description("Gói miễn phí mặc định")
                                .active(true)
                                .build()
                        )
                );
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
        existingPlan.setDailyQuestionLimit(updatedPlan.getDailyQuestionLimit());
        existingPlan.setDurationInMonths(updatedPlan.getDurationInMonths());
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

        if (!RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            throw new BadRequestException("Only student accounts can use subscriptions");
        }

        return user;
    }

    private UserSubscriptionResponse toResponse(UserSubscription subscription) {
        return UserSubscriptionResponse.builder()
                .id(subscription.getId())
                .planId(subscription.getSubscriptionPlan().getId())
                .planName(subscription.getSubscriptionPlan().getName())
                .dailyQuestionLimit(subscription.getSubscriptionPlan().getDailyQuestionLimit())
                .active(subscription.getActive())
                .subscribedAt(subscription.getSubscribedAt())
                .expiresAt(subscription.getExpiresAt())
                .unsubscribedAt(subscription.getUnsubscribedAt())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }
}
