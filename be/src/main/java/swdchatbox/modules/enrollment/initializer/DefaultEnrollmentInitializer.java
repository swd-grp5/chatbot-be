package swdchatbox.modules.enrollment.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.enrollment.entity.UserSubject;
import swdchatbox.modules.enrollment.repository.UserSubjectRepository;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.subject.repository.SubjectRepository;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;

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
