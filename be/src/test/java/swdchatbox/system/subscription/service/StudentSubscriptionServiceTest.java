package swdchatbox.system.subscription.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.document.entity.Subject;
import swdchatbox.system.document.repository.SubjectRepository;
import swdchatbox.system.subscription.entity.StudentSubscription;
import swdchatbox.system.subscription.repository.StudentSubscriptionRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.UserRole;
import swdchatbox.system.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSubscriptionServiceTest {

    @Mock
    private StudentSubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private StudentSubscriptionService subscriptionService;

    @Test
    void subscribe_shouldCreateSubscription_whenNotExists() {
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User student = User.builder()
                .id(userId)
                .email("student@gmail.com")
                .role(UserRole.STUDENT)
                .build();

        Subject subject = Subject.builder()
                .id(subjectId)
                .code("SWD392")
                .name("System Analysis")
                .build();

        StudentSubscription saved = StudentSubscription.builder()
                .id(UUID.randomUUID())
                .student(student)
                .subscriptionPlan(plan)
                .active(true)
                .subscribedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(subscriptionRepository.findByStudent_IdAndSubject_Id(userId, subjectId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(StudentSubscription.class))).thenReturn(saved);

        var response = subscriptionService.subscribe(subjectId, student.getEmail());

        assertEquals(subjectId, response.subjectId());
        assertTrue(response.active());
    }

    @Test
    void subscribe_shouldThrowBadRequest_whenAlreadyActive() {
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User student = User.builder()
                .id(userId)
                .email("student@gmail.com")
                .role(UserRole.STUDENT)
                .build();

        Subject subject = Subject.builder()
                .id(subjectId)
                .code("SWD392")
                .name("System Analysis")
                .build();

        StudentSubscription existing = StudentSubscription.builder()
                .id(UUID.randomUUID())
                .student(student)
                .subject(subject)
                .active(true)
                .subscribedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(subscriptionRepository.findByStudent_IdAndSubject_Id(userId, subjectId)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> subscriptionService.subscribe(subjectId, student.getEmail()));
    }
}

