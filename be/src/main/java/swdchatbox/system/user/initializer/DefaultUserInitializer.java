package swdchatbox.system.user.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.AuthProvider;
import swdchatbox.system.user.enums.UserRole;
import swdchatbox.system.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class DefaultUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("Student", "student@gmail.com", UserRole.STUDENT);
        seedUser("Admin", "admin@gmail.com", UserRole.ADMIN);
        seedUser("Lecturer", "lecture@gmail.com", UserRole.LECTURER);
    }

    private void seedUser(String fullName, String email, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .password(passwordEncoder.encode("123456"))
                .role(role)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .isActive(true)
                .build();

        userRepository.save(user);
    }
}
