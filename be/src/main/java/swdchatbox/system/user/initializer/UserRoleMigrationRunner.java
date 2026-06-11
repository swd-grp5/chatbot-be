package swdchatbox.system.user.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.role.entity.Role;
import swdchatbox.system.role.repository.RoleRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Order(2)
@RequiredArgsConstructor
public class UserRoleMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        migrateFromLegacyRoleColumn();
        assignDefaultRoleToUsersWithoutRole();
    }

    private void migrateFromLegacyRoleColumn() {
        if (!legacyRoleColumnExists()) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, role FROM users WHERE role IS NOT NULL"
        );

        for (Map<String, Object> row : rows) {
            UUID userId = UUID.fromString(row.get("id").toString());
            String roleCode = row.get("role").toString();

            roleRepository.findByCode(roleCode).ifPresent(role ->
                    userRepository.findById(userId).ifPresent(user -> {
                        if (user.getRole() == null) {
                            user.setRole(role);
                            userRepository.save(user);
                        }
                    })
            );
        }
    }

    private void assignDefaultRoleToUsersWithoutRole() {
        Role defaultRole = roleRepository.findByCode(RoleCodes.STUDENT)
                .orElseThrow(() -> new IllegalStateException("Default STUDENT role not found"));

        userRepository.findAll().stream()
                .filter(user -> user.getRole() == null)
                .forEach(user -> {
                    user.setRole(defaultRole);
                    userRepository.save(user);
                });
    }

    private boolean legacyRoleColumnExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND COLUMN_NAME = 'role'
                """,
                Integer.class
        );
        return count != null && count > 0;
    }
}
