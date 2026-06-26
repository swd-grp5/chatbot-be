package swdchatbox.modules.user.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.modules.role.entity.Role;
import swdchatbox.modules.role.repository.RoleRepository;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.enums.AuthProvider;
import swdchatbox.modules.user.repository.UserRepository;

@Component
@Order(3)
@RequiredArgsConstructor
public class DefaultUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("Student", "student@gmail.com", RoleCodes.STUDENT);
        seedUser("Admin", "admin@gmail.com", RoleCodes.ADMIN);
        seedUser("Lecturer", "lecturer@gmail.com", RoleCodes.LECTURER);
    }

    private void seedUser(String fullName, String email, String roleCode) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleCode));

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
