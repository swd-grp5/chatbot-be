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
import swdchatbox.system.subscription.dto.response.StudentSubscriptionResponse;
import swdchatbox.system.subscription.entity.StudentSubscription;
import swdchatbox.system.subscription.entity.SubscriptionPlan;
import swdchatbox.system.subscription.repository.StudentSubscriptionRepository;
import swdchatbox.system.subscription.repository.SubscriptionPlanRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.UserRole;
import swdchatbox.system.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentSubscriptionService {

    private final StudentSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;

    /**
     * 🚀 CẬP NHẬT: Đăng ký gói cước VIP hệ thống (Thay vì môn học)
     */
    @Transactional
    public StudentSubscriptionResponse subscribe(UUID planId, String email) {
        User student = findStudentByEmail(email);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new BadRequestException("This subscription plan is currently inactive");
        }

        // Tìm xem user đang có gói nào active không
        StudentSubscription activeSub = subscriptionRepository
                .findActiveSubscription(student.getId(), LocalDateTime.now())
                .orElse(null);

        if (activeSub != null) {
            // Nếu đang dùng chính gói này và còn hạn thì báo lỗi
            if (activeSub.getSubscriptionPlan().getId().equals(planId)) {
                throw new BadRequestException("You already have an active subscription to this plan");
            }
            // Nếu đổi gói khác (Nâng cấp/Hạ cấp), chủ động hủy gói cũ trước
            activeSub.setActive(false);
            activeSub.setUnsubscribedAt(LocalDateTime.now());
            subscriptionRepository.save(activeSub);
        }

        // Tạo chu kỳ đăng ký mới
        LocalDateTime subscribedAt = LocalDateTime.now();
        LocalDateTime expiresAt = subscribedAt.plusMonths(plan.getDurationInMonths());

        StudentSubscription subscription = StudentSubscription.builder()
                .student(student)
                .subscriptionPlan(plan)
                .active(true)
                .subscribedAt(subscribedAt)
                .expiresAt(expiresAt)
                .build();

        return toResponse(subscriptionRepository.save(subscription));
    }

    /**
     * 🚀 CẬP NHẬT: Hủy gói cước VIP hiện tại
     */
    @Transactional
    public void unsubscribe(String email) {
        User student = findStudentByEmail(email);

        StudentSubscription subscription = subscriptionRepository
                .findActiveSubscription(student.getId(), LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found to unsubscribe"));

        subscription.setActive(false);
        subscription.setUnsubscribedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }

    /**
     * 🚀 YÊU CẦU 1: API Lấy gói người dùng đang có (Mặc định lấy "Free" từ Master SQL của bạn)
     */
    @Transactional(readOnly = true)
    public SubscriptionPlan getCurrentUserPlan(String email) {
        User student = findStudentByEmail(email);

        return subscriptionRepository.findActiveSubscription(student.getId(), LocalDateTime.now())
                .map(StudentSubscription::getSubscriptionPlan)
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

    /**
     * 🚀 YÊU CẦU 3: Lấy tất cả gói cho ADMIN kèm Phân trang + SORT linh hoạt
     */
    @Transactional(readOnly = true)
    public Page<SubscriptionPlan> getAllPlansForAdmin(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return subscriptionPlanRepository.findAll(pageable);
    }

    /**
     * 🚀 YÊU CẦU 2: Admin quản lý gói cước (CRUD)
     */
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
    public List<StudentSubscriptionResponse> findMySubscriptionHistory(String email) {
        User student = findStudentByEmail(email);
        // Lấy lịch sử tất cả các lần mua gói của cơ chế mới
        List<StudentSubscription> subscriptions = subscriptionRepository.findAllByStudent_IdOrderBySubscribedAtDesc(student.getId());
        return subscriptions.stream().map(this::toResponse).toList();
    }

    private User findStudentByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Only student accounts can use subscriptions");
        }

        return user;
    }

    private StudentSubscriptionResponse toResponse(StudentSubscription subscription) {
        return StudentSubscriptionResponse.builder()
                .id(subscription.getId())
                .planId(subscription.getSubscriptionPlan().getId())
                .planName(subscription.getSubscriptionPlan().getName())
                .dailyQuestionLimit(subscription.getSubscriptionPlan().getDailyQuestionLimit())
                .active(subscription.getActive())
                .subscribedAt(subscription.getSubscribedAt())
                .expiresAt(subscription.getExpiresAt())
                .unsubscribedAt(subscription.getUnsubscribedAt())
                .createdAt(subscription.getCreatedAt()) // 🚀 SỬA THÀNH DÒNG NÀY CHO KHỚP ENTITY
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }
}