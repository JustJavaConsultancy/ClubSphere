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
        try {
            // Drop the stale constraint (was missing NETWORK)
            jdbcTemplate.execute(
                "ALTER TABLE post DROP CONSTRAINT IF EXISTS post_post_level_check"
            );
            // Re-create with all current PostLevel enum values
            jdbcTemplate.execute(
                "ALTER TABLE post ADD CONSTRAINT post_post_level_check " +
                "CHECK (post_level IN ('COMMUNITY','GROUP','NETWORK','GENERAL'))"
            );
            System.out.println("DatabaseConstraintMigration: recreated post_post_level_check with NETWORK included");
        } catch (Exception e) {
            // Log but don't fail startup — the constraint may already be correct
            System.err.println("DatabaseConstraintMigration: could not update post_post_level_check — " + e.getMessage());
        }
    }
}


