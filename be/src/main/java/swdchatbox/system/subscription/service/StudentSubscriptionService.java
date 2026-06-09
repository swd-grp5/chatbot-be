package swdchatbox.system.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.entity.Subject;
import swdchatbox.system.document.repository.SubjectRepository;
import swdchatbox.system.subscription.dto.response.StudentSubscriptionResponse;
import swdchatbox.system.subscription.entity.StudentSubscription;
import swdchatbox.system.subscription.repository.StudentSubscriptionRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.UserRole;
import swdchatbox.system.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentSubscriptionService {

    private final StudentSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;

    @Transactional
    public StudentSubscriptionResponse subscribe(UUID subjectId, String email) {
        User student = findStudentByEmail(email);
        Subject subject = findSubject(subjectId);

        StudentSubscription subscription = subscriptionRepository
                .findByStudent_IdAndSubject_Id(student.getId(), subjectId)
                .orElse(null);

        if (subscription == null) {
            subscription = StudentSubscription.builder()
                    .student(student)
                    .subject(subject)
                    .active(true)
                    .subscribedAt(LocalDateTime.now())
                    .build();
        } else if (Boolean.TRUE.equals(subscription.getActive())) {
            throw new BadRequestException("You already subscribed to this subject");
        } else {
            subscription.setActive(true);
            subscription.setSubscribedAt(LocalDateTime.now());
            subscription.setUnsubscribedAt(null);
        }

        return toResponse(subscriptionRepository.save(subscription));
    }

    @Transactional
    public void unsubscribe(UUID subjectId, String email) {
        User student = findStudentByEmail(email);

        StudentSubscription subscription = subscriptionRepository
                .findByStudent_IdAndSubject_Id(student.getId(), subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (!Boolean.TRUE.equals(subscription.getActive())) {
            throw new BadRequestException("Subscription is already inactive");
        }

        subscription.setActive(false);
        subscription.setUnsubscribedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public List<StudentSubscriptionResponse> findMySubscriptions(String email, Boolean active) {
        User student = findStudentByEmail(email);

        List<StudentSubscription> subscriptions = active == null
                ? subscriptionRepository.findAllByStudent_IdOrderBySubscribedAtDesc(student.getId())
                : subscriptionRepository.findAllByStudent_IdAndActiveOrderBySubscribedAtDesc(student.getId(), active);

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

    private Subject findSubject(UUID subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
    }

    private StudentSubscriptionResponse toResponse(StudentSubscription subscription) {
        return StudentSubscriptionResponse.builder()
                .id(subscription.getId())
                .subjectId(subscription.getSubject().getId())
                .subjectCode(subscription.getSubject().getCode())
                .subjectName(subscription.getSubject().getName())
                .active(subscription.getActive())
                .subscribedAt(subscription.getSubscribedAt())
                .unsubscribedAt(subscription.getUnsubscribedAt())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }
}


