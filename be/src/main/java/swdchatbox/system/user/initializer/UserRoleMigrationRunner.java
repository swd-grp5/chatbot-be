package swdchatbox.system.user.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.migration.SchemaMigrationSupport;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class UserRoleMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        SchemaMigrationSupport.fixUserRoleIds(jdbcTemplate);
    }
}
