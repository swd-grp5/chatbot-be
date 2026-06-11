package swdchatbox.system.role.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.role.entity.Role;
import swdchatbox.system.role.repository.RoleRepository;

@Component
@Order(1)
@RequiredArgsConstructor
public class DefaultRoleInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedRole(RoleCodes.ADMIN, "Admin", "System administrator");
        seedRole(RoleCodes.STUDENT, "Student", "Student account");
        seedRole(RoleCodes.LECTURER, "Lecturer", "Lecturer account");
    }

    private void seedRole(String code, String name, String description) {
        if (roleRepository.existsByCode(code)) {
            return;
        }

        Role role = Role.builder()
                .code(code)
                .name(name)
                .description(description)
                .active(true)
                .build();

        roleRepository.save(role);
    }
}
