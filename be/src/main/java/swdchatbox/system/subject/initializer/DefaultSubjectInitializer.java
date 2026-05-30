package swdchatbox.system.subject.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.repository.SubjectRepository;

@Component
@RequiredArgsConstructor
public class DefaultSubjectInitializer implements CommandLineRunner {

    private final SubjectRepository subjectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedSubject("SWD", "SWD", "Default subject for SWD chatbox");
    }

    private void seedSubject(String code, String name, String description) {
        if (subjectRepository.existsByCode(code)) {
            return;
        }

        Subject subject = Subject.builder()
                .code(code)
                .name(name)
                .description(description)
                .active(true)
                .build();

        subjectRepository.save(subject);
    }
}
