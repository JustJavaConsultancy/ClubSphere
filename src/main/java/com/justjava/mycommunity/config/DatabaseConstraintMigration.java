package com.justjava.mycommunity.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time migration to drop the stale Hibernate-generated check constraint
 * on the "post" table's post_level column.
 *
 * Hibernate's ddl-auto=update does NOT update existing check constraints when
 * new enum values (e.g. NETWORK) are added to PostLevel.  Dropping the old
 * constraint lets Hibernate re-create it with all current enum values on the
 * next schema update.
 */
@Component
@Order(0)   // run before other ApplicationRunners
@RequiredArgsConstructor
public class DatabaseConstraintMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // ── Fix 1: post_post_level_check (missing NETWORK) ──────────────────────
        try {
            jdbcTemplate.execute(
                "ALTER TABLE post DROP CONSTRAINT IF EXISTS post_post_level_check"
            );
            jdbcTemplate.execute(
                "ALTER TABLE post ADD CONSTRAINT post_post_level_check " +
                "CHECK (post_level IN ('COMMUNITY','GROUP','NETWORK','GENERAL'))"
            );
            System.out.println("DatabaseConstraintMigration: recreated post_post_level_check with NETWORK included");
        } catch (Exception e) {
            System.err.println("DatabaseConstraintMigration: could not update post_post_level_check — " + e.getMessage());
        }

        // ── Fix 2: community_membership_status_check (missing SUSPENDED) ────────
        try {
            // Only drop & recreate if the constraint exists but is missing SUSPENDED
            String constraintDef = null;
            try {
                constraintDef = jdbcTemplate.queryForObject(
                    "SELECT pg_get_constraintdef(c.oid) " +
                    "FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid " +
                    "WHERE t.relname = 'community_membership' " +
                    "  AND c.conname = 'community_membership_status_check'",
                    String.class
                );
            } catch (Exception ignored) {
                // constraint doesn't exist — nothing to fix
            }

            if (constraintDef != null && !constraintDef.contains("SUSPENDED")) {
                jdbcTemplate.execute(
                    "ALTER TABLE community_membership DROP CONSTRAINT community_membership_status_check"
                );
                jdbcTemplate.execute(
                    "ALTER TABLE community_membership ADD CONSTRAINT community_membership_status_check " +
                    "CHECK (status IN ('PENDING','APPROVED','REJECTED','SUSPENDED'))"
                );
                System.out.println("DatabaseConstraintMigration: recreated community_membership_status_check with SUSPENDED included");
            }
        } catch (Exception e) {
            System.err.println("DatabaseConstraintMigration: could not update community_membership_status_check — " + e.getMessage());
        }
    }
}


