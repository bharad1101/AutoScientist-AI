package com.autoscientist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class LegacyPasswordBackfillTest {

    @Test
    @DisplayName("Legacy password column backfill is accurate, idempotent, and non-destructive")
    void testLegacyPasswordBackfillLogic() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:legacy_test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 1. Create a table mimicking a legacy schema with 'password' column and newly added 'password_hash' column
        jdbc.execute("CREATE TABLE users (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "email VARCHAR(255) NOT NULL, " +
                "password VARCHAR(255) NULL, " +
                "password_hash VARCHAR(255) NULL" +
                ")");

        // 2. Insert 3 rows:
        // Row 1: Legacy unmigrated user (password has hash, password_hash is NULL)
        jdbc.update("INSERT INTO users (email, password, password_hash) VALUES ('legacy1@science.org', '$2a$10$legacyHash11111111111', NULL)");
        // Row 2: Legacy unmigrated user (password has hash, password_hash is empty string)
        jdbc.update("INSERT INTO users (email, password, password_hash) VALUES ('legacy2@science.org', '$2a$10$legacyHash22222222222', '')");
        // Row 3: Modern migrated user (password_hash already has modern hash, legacy password is NULL)
        jdbc.update("INSERT INTO users (email, password, password_hash) VALUES ('modern@science.org', NULL, '$2a$10$modernHash33333333333')");

        // 3. Execute the idempotent backfill query
        int updatedRows = jdbc.update(
                "UPDATE users SET password_hash = password WHERE (password_hash IS NULL OR password_hash = '') AND password IS NOT NULL"
        );

        // Exactly the 2 legacy rows should be updated
        assertThat(updatedRows).isEqualTo(2);

        // Verify row 1 now has password_hash populated
        String hash1 = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'legacy1@science.org'", String.class);
        assertThat(hash1).isEqualTo("$2a$10$legacyHash11111111111");

        // Verify row 2 now has password_hash populated
        String hash2 = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'legacy2@science.org'", String.class);
        assertThat(hash2).isEqualTo("$2a$10$legacyHash22222222222");

        // Verify modern row was untouched
        String hash3 = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'modern@science.org'", String.class);
        assertThat(hash3).isEqualTo("$2a$10$modernHash33333333333");

        // 4. Test IDEMPOTENCY: Re-running the query must update 0 rows
        int secondRunUpdates = jdbc.update(
                "UPDATE users SET password_hash = password WHERE (password_hash IS NULL OR password_hash = '') AND password IS NOT NULL"
        );
        assertThat(secondRunUpdates).isEqualTo(0);
    }
}
