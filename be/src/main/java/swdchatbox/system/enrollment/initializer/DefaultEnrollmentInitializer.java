package swdchatbox.system.enrollment.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.enrollment.entity.UserSubject;
import swdchatbox.system.enrollment.repository.UserSubjectRepository;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.repository.SubjectRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

@Component
@Order(6)
@RequiredArgsConstructor
public class DefaultEnrollmentInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final UserSubjectRepository userSubjectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Subject subject = subjectRepository.findByCode("SWD").orElse(null);
        if (subject == null) {
            return;
        }

        assignIfMissing("student@gmail.com", subject);
        assignIfMissing("lecturer@gmail.com", subject);
    }

    private void assignIfMissing(String email, Subject subject) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        if (userSubjectRepository.existsByUser_IdAndSubject_Id(user.getId(), subject.getId())) {
            return;
        }

        userSubjectRepository.save(UserSubject.builder()
                .user(user)
                .subject(subject)
                .build());
    }
}
