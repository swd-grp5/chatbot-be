package swdchatbox.system.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.repository.SubjectRepository;
import swdchatbox.system.subscription.entity.StudentSubscription;
import swdchatbox.system.subscription.repository.StudentSubscriptionRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.UserRole;
import swdchatbox.system.user.repository.UserRepository;
import swdchatbox.system.wallet.service.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionPurchaseService {

    private final StudentSubscriptionRepository subscriptionRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    @Transactional
    public StudentSubscription purchase(UUID subjectId, String email, BigDecimal price) {
        User student = findStudentByEmail(email);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        walletService.debitWallet(email, price, "SUB-" + subjectId, "Subscription purchase for subject " + subject.getCode());

        StudentSubscription subscription = subscriptionRepository
                .findByStudent_IdAndSubject_Id(student.getId(), subjectId)
                .orElse(StudentSubscription.builder()
                        .student(student)
                        .subject(subject)
                        .build());

        subscription.setActive(true);
        subscription.setSubscribedAt(LocalDateTime.now());
        subscription.setUnsubscribedAt(null);
        return subscriptionRepository.save(subscription);
    }

    private User findStudentByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Only student accounts can buy subscriptions");
        }
        return user;
    }
}
