package swdchatbox.shared.migration;

import org.springframework.jdbc.core.JdbcTemplate;

public final class SchemaMigrationSupport {

    private SchemaMigrationSupport() {
    }

    public static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
