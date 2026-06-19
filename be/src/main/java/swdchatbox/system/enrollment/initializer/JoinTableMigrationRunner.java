package swdchatbox.system.enrollment.initializer;

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
@Order(3)
@RequiredArgsConstructor
public class JoinTableMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        SchemaMigrationSupport.ensureUserSubjectsTable(jdbcTemplate);
        migrateUserSubjectAssignments();
        migrateStudentSubscriptions();
        migrateInvoiceSubscriptionLink();
        migrateInvoiceOwnership();
        migratePaymentsToInvoiceOnly();
        dropLegacySchemaArtifacts();
    }

    private void migrateUserSubjectAssignments() {
        if (!tableExists("subjects") || !tableExists("users")) {
            return;
        }

        if (tableExists("lecturer_subjects")) {
            int migrated = jdbcTemplate.update("""
                    INSERT IGNORE INTO user_subjects (id, user_id, subject_id, created_at)
                    SELECT UUID_TO_BIN(UUID()), lecturer_id, subject_id, NOW(6)
                    FROM lecturer_subjects
                    """);
            if (migrated > 0) {
                log.info("Migrated {} row(s) from lecturer_subjects to user_subjects", migrated);
            }
        }

        if (tableExists("student_subjects")) {
            int migrated = jdbcTemplate.update("""
                    INSERT IGNORE INTO user_subjects (id, user_id, subject_id, created_at)
                    SELECT UUID_TO_BIN(UUID()), student_id, subject_id, NOW(6)
                    FROM student_subjects
                    """);
            if (migrated > 0) {
                log.info("Migrated {} row(s) from student_subjects to user_subjects", migrated);
            }
        }

        if (columnExists("subjects", "user_id")) {
            int migrated = jdbcTemplate.update("""
                    INSERT IGNORE INTO user_subjects (id, user_id, subject_id, created_at)
                    SELECT UUID_TO_BIN(UUID()), user_id, id, NOW(6)
                    FROM subjects
                    WHERE user_id IS NOT NULL
                    """);
            if (migrated > 0) {
                log.info("Migrated {} row(s) from subjects.user_id to user_subjects", migrated);
            }
        }

        if (columnExists("subjects", "lecturer_id")) {
            int migrated = jdbcTemplate.update("""
                    INSERT IGNORE INTO user_subjects (id, user_id, subject_id, created_at)
                    SELECT UUID_TO_BIN(UUID()), lecturer_id, id, NOW(6)
                    FROM subjects
                    WHERE lecturer_id IS NOT NULL
                    """);
            if (migrated > 0) {
                log.info("Migrated {} row(s) from subjects.lecturer_id to user_subjects", migrated);
            }
        }

        if (columnExists("users", "assigned_subject_ids")) {
            try {
                int migrated = jdbcTemplate.update("""
                        INSERT IGNORE INTO user_subjects (id, user_id, subject_id, created_at)
                        SELECT UUID_TO_BIN(UUID()), u.id, UUID_TO_BIN(jt.subject_id), NOW(6)
                        FROM users u
                        JOIN JSON_TABLE(
                            u.assigned_subject_ids,
                            '$[*]' COLUMNS (subject_id CHAR(36) PATH '$')
                        ) jt
                        WHERE u.assigned_subject_ids IS NOT NULL
                          AND JSON_LENGTH(u.assigned_subject_ids) > 0
                        """);
                if (migrated > 0) {
                    log.info("Migrated {} row(s) from users.assigned_subject_ids to user_subjects", migrated);
                }
            } catch (Exception ex) {
                log.warn("Skipped JSON subject migration: {}", ex.getMessage());
            }
        }

        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "subjects", "user_id");
        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "subjects", "lecturer_id");
        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "users", "assigned_subject_ids");
    }

    private void migrateStudentSubscriptions() {
        if (!tableExists("student_subscriptions") || !tableExists("user_subscriptions")) {
            return;
        }
        int migrated = jdbcTemplate.update("""
                INSERT IGNORE INTO user_subscriptions (
                    id, user_id, subscription_plan_id, active,
                    subscribed_at, expires_at, unsubscribed_at, created_at, updated_at
                )
                SELECT
                    id, student_id, subscription_plan_id, active,
                    subscribed_at, expires_at, unsubscribed_at, created_at, updated_at
                FROM student_subscriptions
                """);
        if (migrated > 0) {
            log.info("Migrated {} row(s) from student_subscriptions to user_subscriptions", migrated);
        }
    }

    private void migrateInvoiceSubscriptionLink() {
        if (!tableExists("invoices") || !tableExists("user_subscriptions")) {
            return;
        }

        if (!columnExists("invoices", "subscription_plan_id")) {
            return;
        }

        if (!columnExists("invoices", "user_subscription_id")) {
            jdbcTemplate.execute("""
                    ALTER TABLE invoices
                    ADD COLUMN user_subscription_id BINARY(16) NULL
                    """);
            log.info("Added invoices.user_subscription_id column");
        }

        int linked = jdbcTemplate.update("""
                UPDATE invoices i
                INNER JOIN user_subscriptions us
                    ON us.user_id = i.user_id
                    AND us.subscription_plan_id = i.subscription_plan_id
                    AND us.subscribed_at <= i.issued_at
                LEFT JOIN user_subscriptions newer
                    ON newer.user_id = i.user_id
                    AND newer.subscription_plan_id = i.subscription_plan_id
                    AND newer.subscribed_at <= i.issued_at
                    AND newer.subscribed_at > us.subscribed_at
                SET i.user_subscription_id = us.id
                WHERE i.subscription_plan_id IS NOT NULL
                  AND i.user_subscription_id IS NULL
                  AND newer.id IS NULL
                """);

        if (linked > 0) {
            log.info("Linked {} subscription invoice(s) to user_subscriptions", linked);
        }

        if (columnExists("invoices", "type")) {
            jdbcTemplate.update("""
                    UPDATE invoices
                    SET type = 'SUBSCRIPTION'
                    WHERE type IS NULL AND subscription_plan_id IS NOT NULL
                    """);
        }

        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "invoices", "subscription_plan_id");
    }

    private void migrateInvoiceOwnership() {
        if (!tableExists("invoices")) {
            return;
        }

        if (!columnExists("invoices", "wallet_id")) {
            jdbcTemplate.execute("""
                    ALTER TABLE invoices
                    ADD COLUMN wallet_id BINARY(16) NULL
                    """);
            log.info("Added invoices.wallet_id column");
        }

        if (columnExists("invoices", "user_id") && tableExists("wallets")) {
            jdbcTemplate.update("""
                    INSERT INTO wallets (id, user_id, balance, reserved_balance, active, created_at, updated_at)
                    SELECT UUID_TO_BIN(UUID()), i.user_id, 0, 0, 1, NOW(6), NOW(6)
                    FROM invoices i
                    WHERE i.user_id IS NOT NULL
                      AND (i.type = 'WALLET_TOPUP' OR i.user_subscription_id IS NULL)
                      AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.user_id = i.user_id)
                    GROUP BY i.user_id
                    """);

            int linked = jdbcTemplate.update("""
                    UPDATE invoices i
                    INNER JOIN wallets w ON w.user_id = i.user_id
                    SET i.wallet_id = w.id
                    WHERE i.wallet_id IS NULL
                      AND i.user_id IS NOT NULL
                      AND (i.type = 'WALLET_TOPUP' OR i.user_subscription_id IS NULL)
                    """);

            if (linked > 0) {
                log.info("Linked {} wallet top-up invoice(s) to wallets", linked);
            }

            SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "invoices", "user_id");
        }
    }

    private void migratePaymentsToInvoiceOnly() {
        if (!tableExists("payments") || !tableExists("invoices")) {
            return;
        }

        backfillInvoiceType();

        if (!columnExists("payments", "user_id") || !columnExists("payments", "invoice_id")) {
            return;
        }

        if (columnExists("payments", "user_email")) {
            jdbcTemplate.update("""
                    UPDATE payments p
                    INNER JOIN users u ON u.email = p.user_email
                    SET p.user_id = u.id
                    WHERE p.user_id IS NULL
                    """);
        }

        if (tableExists("wallets")) {
            jdbcTemplate.update("""
                    INSERT INTO wallets (id, user_id, balance, reserved_balance, active, created_at, updated_at)
                    SELECT UUID_TO_BIN(UUID()), p.user_id, 0, 0, 1, NOW(6), NOW(6)
                    FROM payments p
                    WHERE p.invoice_id IS NULL
                      AND p.user_id IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.user_id = p.user_id)
                    GROUP BY p.user_id
                    """);
        }

        int created = jdbcTemplate.update("""
                INSERT INTO invoices (
                    id, invoice_number, wallet_id, amount, status,
                    plan_name, description, issued_at, paid_at, type, created_at, updated_at
                )
                SELECT
                    UUID_TO_BIN(UUID()),
                    CONCAT('INV-MIG-', LOWER(REPLACE(BIN_TO_UUID(p.id), '-', ''))),
                    w.id,
                    p.amount,
                    CASE p.payment_status WHEN 'SUCCESS' THEN 'PAID' ELSE 'PENDING' END,
                    'Wallet Top-Up',
                    COALESCE(p.description, 'Migrated wallet payment'),
                    COALESCE(p.created_at, NOW(6)),
                    CASE p.payment_status WHEN 'SUCCESS' THEN p.updated_at ELSE NULL END,
                    'WALLET_TOPUP',
                    COALESCE(p.created_at, NOW(6)),
                    COALESCE(p.updated_at, NOW(6))
                FROM payments p
                INNER JOIN wallets w ON w.user_id = p.user_id
                WHERE p.invoice_id IS NULL AND p.user_id IS NOT NULL
                """);

        int linked = jdbcTemplate.update("""
                UPDATE payments p
                INNER JOIN invoices i
                    ON i.invoice_number = CONCAT('INV-MIG-', LOWER(REPLACE(BIN_TO_UUID(p.id), '-', '')))
                SET p.invoice_id = i.id
                WHERE p.invoice_id IS NULL
                """);

        if (created > 0 || linked > 0) {
            log.info("Linked {} legacy payment(s) to wallet invoice(s)", linked);
        }

        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "payments", "user_id");
    }

    private void backfillInvoiceType() {
        if (!columnExists("invoices", "type")) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE invoices
                SET type = 'SUBSCRIPTION'
                WHERE type IS NULL AND user_subscription_id IS NOT NULL
                """);
        jdbcTemplate.update("""
                UPDATE invoices
                SET type = 'WALLET_TOPUP'
                WHERE type IS NULL
                """);
    }

    private void dropLegacySchemaArtifacts() {
        dropTableIfExists("student_subjects");
        dropTableIfExists("lecturer_subjects");
        dropTableIfExists("student_subscriptions");

        dropColumnIfExists("invoices", "payment_id");
        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "payments", "user_email");
        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, "payments", "user_id");
        dropColumnIfExists("payments", "reference_type");
        dropColumnIfExists("payments", "reference_id");
    }

    private void dropTableIfExists(String tableName) {
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
        log.info("Dropped legacy table {}", tableName);
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        SchemaMigrationSupport.dropColumnIfExists(jdbcTemplate, tableName, columnName);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
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

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
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
