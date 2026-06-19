package swdchatbox.system.common.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

@Slf4j
public final class SchemaMigrationSupport {

    private static final String STUDENT_ROLE_SUBQUERY = """
            (SELECT id FROM roles WHERE code = 'STUDENT' LIMIT 1)
            """;

    private SchemaMigrationSupport() {
    }

    public static void ensureUserSubjectsTable(JdbcTemplate jdbc) {
        if (tableExists(jdbc, "user_subjects")) {
            return;
        }
        jdbc.execute("""
                CREATE TABLE user_subjects (
                    id BINARY(16) NOT NULL,
                    user_id BINARY(16) NOT NULL,
                    subject_id BINARY(16) NOT NULL,
                    created_at DATETIME(6) NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_user_subject (user_id, subject_id),
                    KEY idx_user_subject_user (user_id),
                    KEY idx_user_subject_subject (subject_id)
                )
                """);
        log.info("Created missing table user_subjects");
    }

    public static void fixUserRoleIds(JdbcTemplate jdbc) {
        if (!tableExists(jdbc, "roles") || !tableExists(jdbc, "users")) {
            return;
        }
        if (!columnExists(jdbc, "users", "role_id")) {
            return;
        }

        seedDefaultRoles(jdbc);
        normalizeUserRoleIdColumn(jdbc);
        repairInvalidUserRoleIds(jdbc);
    }

    private static void normalizeUserRoleIdColumn(JdbcTemplate jdbc) {
        String columnType = getColumnType(jdbc, "users", "role_id");
        if (columnType == null || isBinaryUuidColumn(columnType)) {
            return;
        }

        log.info("Pre-JPA: converting users.role_id from {} to BINARY(16)", columnType);
        dropForeignKeysOnColumn(jdbc, "users", "role_id");
        dropColumnIfExists(jdbc, "users", "role_id_uuid");

        jdbc.execute("ALTER TABLE users ADD COLUMN role_id_uuid BINARY(16) NULL");

        if (columnExists(jdbc, "users", "role")) {
            jdbc.update("""
                    UPDATE users u
                    INNER JOIN roles r ON r.code = u.role
                    SET u.role_id_uuid = r.id
                    WHERE u.role IS NOT NULL
                    """);
        }

        try {
            jdbc.update("""
                    UPDATE users u
                    INNER JOIN roles r ON r.code = TRIM(CAST(u.role_id AS CHAR))
                    SET u.role_id_uuid = r.id
                    WHERE u.role_id_uuid IS NULL
                      AND u.role_id IS NOT NULL
                    """);
        } catch (DataAccessException ex) {
            log.warn("Pre-JPA: skipped role code mapping from legacy role_id strings: {}", ex.getMessage());
        }

        jdbc.update("""
                UPDATE users
                SET role_id_uuid = %s
                WHERE role_id_uuid IS NULL
                """.formatted(STUDENT_ROLE_SUBQUERY));

        dropColumnIfExists(jdbc, "users", "role_id");
        jdbc.execute("ALTER TABLE users CHANGE role_id_uuid role_id BINARY(16) NOT NULL");
        log.info("Pre-JPA: users.role_id is now BINARY(16)");
    }

    private static void repairInvalidUserRoleIds(JdbcTemplate jdbc) {
        if (!columnExists(jdbc, "users", "role_id")) {
            return;
        }

        try {
            int nullFixed = jdbc.update("""
                    UPDATE users
                    SET role_id = %s
                    WHERE role_id IS NULL
                    """.formatted(STUDENT_ROLE_SUBQUERY));

            int invalidFixed = jdbc.update("""
                    UPDATE users u
                    SET u.role_id = %s
                    WHERE u.role_id IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM roles r WHERE r.id = u.role_id)
                    """.formatted(STUDENT_ROLE_SUBQUERY));

            if (nullFixed > 0 || invalidFixed > 0) {
                log.info("Pre-JPA: repaired role_id for {} user(s)", nullFixed + invalidFixed);
            }
        } catch (DataAccessException ex) {
            log.warn("Pre-JPA: skipped user role_id repair: {}", ex.getMessage());
        }
    }

    public static void dropForeignKeysOnColumn(JdbcTemplate jdbc, String tableName, String columnName) {
        List<String> fkNames = jdbc.queryForList("""
                SELECT tc.CONSTRAINT_NAME
                FROM information_schema.TABLE_CONSTRAINTS tc
                INNER JOIN information_schema.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                    AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                    AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
                    AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.TABLE_SCHEMA = DATABASE()
                  AND tc.TABLE_NAME = ?
                  AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
                  AND kcu.COLUMN_NAME = ?
                """, String.class, tableName, columnName);

        for (String fkName : fkNames) {
            jdbc.execute("ALTER TABLE `" + tableName + "` DROP FOREIGN KEY `" + fkName + "`");
            log.info("Dropped foreign key {} on {}.{}", fkName, tableName, columnName);
        }
    }

    public static void dropColumnIfExists(JdbcTemplate jdbc, String tableName, String columnName) {
        if (!columnExists(jdbc, tableName, columnName)) {
            return;
        }
        dropForeignKeysOnColumn(jdbc, tableName, columnName);
        try {
            jdbc.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName + "`");
            log.info("Dropped legacy column {}.{}", tableName, columnName);
        } catch (DataAccessException ex) {
            log.warn("Could not drop column {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }

    private static void seedDefaultRoles(JdbcTemplate jdbc) {
        insertRoleIfMissing(jdbc, "ADMIN", "Admin", "System administrator");
        insertRoleIfMissing(jdbc, "STUDENT", "Student", "Student account");
        insertRoleIfMissing(jdbc, "LECTURER", "Lecturer", "Lecturer account");
    }

    private static void insertRoleIfMissing(JdbcTemplate jdbc, String code, String name, String description) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE code = ?",
                Integer.class,
                code
        );
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO roles (id, code, name, description, active, created_at, updated_at)
                VALUES (UUID_TO_BIN(UUID()), ?, ?, ?, 1, NOW(6), NOW(6))
                """, code, name, description);
        log.info("Pre-JPA: seeded role {}", code);
    }

    private static String getColumnType(JdbcTemplate jdbc, String tableName, String columnName) {
        return jdbc.query("""
                        SELECT COLUMN_TYPE
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                rs -> rs.next() ? rs.getString("COLUMN_TYPE") : null,
                tableName,
                columnName
        );
    }

    private static boolean isBinaryUuidColumn(String columnType) {
        String lower = columnType.toLowerCase(Locale.ROOT);
        return lower.startsWith("binary(16)") || lower.startsWith("varbinary(16)");
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

    public static boolean columnExists(JdbcTemplate jdbc, String tableName, String columnName) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
