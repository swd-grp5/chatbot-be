package swdchatbox.system.user.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.role.entity.Role;
import swdchatbox.system.role.repository.RoleRepository;

import java.util.UUID;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class UserRoleMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
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

        int updated = jdbcTemplate.update("""
                UPDATE users u
                INNER JOIN roles r ON r.code = u.role
                SET u.role_id = r.id
                WHERE u.role IS NOT NULL
                """);

        log.info("Migrated role_id from legacy role column for {} user(s)", updated);
    }

    private void assignDefaultRoleToUsersWithoutRole() {
        UUID defaultRoleId = roleRepository.findByCode(RoleCodes.STUDENT)
                .map(Role::getId)
                .orElseThrow(() -> new IllegalStateException("Default STUDENT role not found"));

        int updated = jdbcTemplate.update("""
                UPDATE users u
                LEFT JOIN roles r ON u.role_id = r.id
                SET u.role_id = ?
                WHERE r.id IS NULL
                """, defaultRoleId);

        if (updated > 0) {
            log.info("Assigned default STUDENT role to {} user(s) with missing or invalid role_id", updated);
        }
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
