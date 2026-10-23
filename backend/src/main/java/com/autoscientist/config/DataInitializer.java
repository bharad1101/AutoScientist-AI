package com.autoscientist.config;

import com.autoscientist.entity.User;
import com.autoscientist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Initializes database compatibility and default development seed data.
 * Does not overwrite or delete existing users.
 * Does not bypass password authentication or security.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Override
    public void run(String... args) {
        backfillLegacyPasswordsIfPresent();
        seedDevUsersIfEmpty();
    }

    /**
     * Safe, non-destructive migration helper:
     * If a legacy database has existing accounts with the legacy 'password' column
     * but 'password_hash' is null (e.g. before Flyway V2 execution), backfill
     * password_hash without altering the passwords or dropping existing columns.
     */
    private void backfillLegacyPasswordsIfPresent() {
        try {
            boolean hasLegacyColumn = false;
            if (jdbcTemplate.getDataSource() != null) {
                try (var conn = jdbcTemplate.getDataSource().getConnection()) {
                    var meta = conn.getMetaData();
                    try (var rs = meta.getColumns(null, null, "users", "password")) {
                        if (rs.next()) {
                            hasLegacyColumn = true;
                        }
                    }
                    if (!hasLegacyColumn) {
                        try (var rs = meta.getColumns(null, null, "USERS", "PASSWORD")) {
                            if (rs.next()) {
                                hasLegacyColumn = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Database metadata inspection fallback: {}", e.getMessage());
                }
            }

            if (hasLegacyColumn) {
                int updated = jdbcTemplate.update(
                        "UPDATE users SET password_hash = password WHERE (password_hash IS NULL OR password_hash = '') AND password IS NOT NULL"
                );
                if (updated > 0) {
                    log.info("Backfilled password_hash from legacy password column for {} user accounts.", updated);
                } else {
                    log.debug("Legacy password column present, but no accounts required backfill (idempotent).");
                }
            }
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            // Normal when 'password' column does not exist in modern schema or in-memory DB
            log.trace("Legacy password column check: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Notice: unexpected error during legacy password backfill check: {}", e.getMessage(), e);
        }
    }

    /**
     * Seeds initial demo researcher accounts ONLY in the 'dev' profile and ONLY when
     * the database is completely empty (0 users). Never modifies or touches production
     * or staging environments.
     */
    private void seedDevUsersIfEmpty() {
        if (environment.acceptsProfiles(Profiles.of("dev")) && userRepository.count() == 0) {
            log.info("Dev profile active and user repository empty. Seeding initial development researcher accounts...");

            User demoResearcher = User.builder()
                    .name("Dr. Jane Doe")
                    .email("jane@university.edu")
                    .passwordHash(passwordEncoder.encode("SecurePassword123!"))
                    .role("ROLE_USER")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            User adminResearcher = User.builder()
                    .name("Admin Researcher")
                    .email("admin@autoscientist.com")
                    .passwordHash(passwordEncoder.encode("science123"))
                    .role("ROLE_ADMIN")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            userRepository.save(demoResearcher);
            userRepository.save(adminResearcher);

            log.info("Initial development research accounts created successfully (jane@university.edu, admin@autoscientist.com).");
        }
    }
}
