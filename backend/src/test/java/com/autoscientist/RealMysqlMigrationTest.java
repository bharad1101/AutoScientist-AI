package com.autoscientist;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-MySQL migration integration test. Requires a server-level staging URL,
 * credentials, and the explicit {@code AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP=true}
 * opt-in. Each invocation creates uniquely named schemas and drops only schemas
 * it successfully created. Never point this test at production.
 *
 * <p>H2 in MODE=MySQL is a useful proxy but is NOT sufficient: it accepts SQL
 * MySQL rejects ({@code CREATE INDEX IF NOT EXISTS}) and resolves table
 * references differently at PREPARE time. Both bugs were caught here and not on
 * H2. This test therefore exercises the two real paths on real MySQL:
 *
 * <ol>
 *   <li><b>fresh install</b> — empty schema, Flyway V1–V4, then Hibernate
 *       {@code ddl-auto=validate} must find the schema complete.</li>
 *   <li><b>legacy pre-Flyway upgrade</b> — a V1 schema transformed to known
 *       legacy column differences, with a live row; Flyway baselines at V1 and
 *       applies V2–V4, then the full application context runs schema validation.</li>
 * </ol>
 *
 * Run only against disposable staging and set
 * {@code AS_TEST_MYSQL_ENVIRONMENT=staging} and
 * {@code AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP=true}. Read credentials from
 * a secret manager; never use production credentials. Example:
 * Configure those environment variables from the secret manager, then invoke
 * the test from this Maven module.
 * The URL must end at the server (no default schema) and require TLS via
 * {@code sslMode=REQUIRED} or a certificate-verifying mode.
 */
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_ENVIRONMENT", matches = "staging")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP", matches = "true")
class RealMysqlMigrationTest {

    private static final String URL = System.getenv("AS_TEST_MYSQL_URL");
    private static final String USER = System.getenv("AS_TEST_MYSQL_USER");
    private static final String PASSWORD = System.getenv("AS_TEST_MYSQL_PASSWORD");

    private static String databaseUrl(String database) {
        String base = URL.trim();
        int queryIndex = base.indexOf('?');
        String location = queryIndex < 0 ? base : base.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : base.substring(queryIndex + 1);
        if (!location.matches("jdbc:mysql://[^/]+/?")) {
            throw new IllegalArgumentException("AS_TEST_MYSQL_URL must not include a default schema.");
        }
        if (query.matches("(?is).*(^|&)sslMode=(DISABLED|PREFERRED)(&.*|$).*")) {
            throw new IllegalArgumentException("Migration tests require TLS; use sslMode=REQUIRED or VERIFY_*.");
        }
        if (!query.matches("(?is).*(^|&)sslMode=(REQUIRED|VERIFY_CA|VERIFY_IDENTITY)(&.*|$).*")) {
            query = query.isBlank() ? "sslMode=REQUIRED" : query + "&sslMode=REQUIRED";
        }
        if (!query.matches("(?is).*(^|&)serverTimezone=.*")) query += "&serverTimezone=UTC";
        return location.replaceAll("/$", "") + "/" + database + "?" + query;
    }

    private static String uniqueSchema(String purpose) {
        return "as_it_" + purpose + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private DataSource dataSource(String database) {
        org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(databaseUrl(database));
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        return ds;
    }

    private void exec(DataSource ds, String... sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String stmt : sql) {
                s.execute(stmt);
            }
        }
    }

    private int scalar(DataSource ds, String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Flyway flyway(DataSource ds, boolean baseline) {
        return Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baseline)
                .baselineVersion("1")
                .load();
    }

    private void validateWithApplication(String schemaUrl) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(AutoScientistApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=mysql",
                        "--spring.main.banner-mode=off",
                        "--spring.datasource.url=" + schemaUrl,
                        "--spring.datasource.username=" + USER,
                        "--spring.datasource.password=" + PASSWORD,
                        "--spring.flyway.enabled=true",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--app.jwt.secret=" + java.util.Base64.getEncoder().encodeToString(new byte[64]),
                        "--app.cors.allowed-origins=https://staging.invalid",
                        "--app.password-reset.frontend-url=https://staging.invalid",
                        "--app.upload.dir=" + java.nio.file.Path.of("target", "mysql-test-uploads").toAbsolutePath())) {
            // The real application context performs Flyway and Hibernate validation.
        }
    }

    @Test
    void freshInstallMigratesAndMatchesEntities() throws Exception {
        String db = uniqueSchema("fresh");
        DataSource root = dataSource("");
        String schemaUrl = databaseUrl(db);
        boolean created = false;
        try {
            exec(root, "CREATE DATABASE " + db + " CHARACTER SET utf8mb4");
            created = true;
            DataSource ds = dataSource(db);
            var result = flyway(ds, false).migrate();
            assertThat(result.migrationsExecuted).as("fresh install runs V1–V4").isEqualTo(4);
            assertThat(scalar(ds, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL"))
                    .as("all four versioned migrations are recorded").isEqualTo(4);
            validateWithApplication(schemaUrl);
        } finally {
            if (created) exec(root, "DROP DATABASE " + db);
        }
    }

    @Test
    void legacyPreFlywayDatabaseUpgradesWithoutDataLoss() throws Exception {
        String db = uniqueSchema("legacy");
        DataSource root = dataSource("");
        String schemaUrl = databaseUrl(db);
        boolean created = false;
        try {
            exec(root, "CREATE DATABASE " + db + " CHARACTER SET utf8mb4");
            created = true;
            DataSource ds = dataSource(db);
            // Start from the complete V1 table set, then roll selected columns
            // back to the known pre-Flyway shape. This lets the real Hibernate
            // validator check every entity after the additive migration path.
            Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                    .target("1").load().migrate();
            exec(ds,
                    "ALTER TABLE users CHANGE COLUMN password_hash password VARCHAR(255) NOT NULL",
                    "ALTER TABLE users DROP COLUMN created_at",
                    "ALTER TABLE datasets DROP COLUMN name, DROP COLUMN version, DROP COLUMN created_at, DROP COLUMN source_stored_filename",
                    "ALTER TABLE datasets ADD COLUMN file_path VARCHAR(1024) NOT NULL DEFAULT '/data/legacy.csv', "
                            + "ADD COLUMN file_type VARCHAR(255) NOT NULL DEFAULT 'text/csv', "
                            + "ADD COLUMN uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "ALTER TABLE hypotheses DROP COLUMN null_hypothesis, DROP COLUMN alt_hypothesis, DROP COLUMN var1",
                    "ALTER TABLE hypotheses ADD COLUMN hypothesis_text TEXT NULL, "
                            + "ADD COLUMN alternative_hypothesis TEXT NULL, ADD COLUMN variables VARCHAR(255) NULL",
                    "ALTER TABLE associations DROP COLUMN ci_lower, DROP COLUMN ci_upper, DROP COLUMN ci_level, "
                            + "DROP COLUMN ci_method, DROP COLUMN ci_target, DROP COLUMN ci_unavailable_reason, "
                            + "DROP COLUMN effect_size_ci_lower, DROP COLUMN effect_size_ci_upper",
                    "ALTER TABLE statistical_tests DROP COLUMN ci_lower, DROP COLUMN ci_upper, DROP COLUMN ci_level, "
                            + "DROP COLUMN ci_method, DROP COLUMN ci_target, DROP COLUMN ci_unavailable_reason",
                    "ALTER TABLE data_quality_reports DROP INDEX uq_quality_dataset",
                    "DROP TABLE analysis_runs, drift_snapshots, password_reset_tokens",
                    "DROP TABLE flyway_schema_history",
                    "INSERT INTO users (email,name,password,role) VALUES "
                            + "('legacy@example.com','Legacy','$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345','ROLE_USER')",
                    "INSERT INTO datasets (user_id,original_filename,stored_filename,file_path,file_type,file_size,"
                            + "row_count,column_count,uploaded_at) VALUES "
                            + "(1,'old.csv','old-uuid.csv','/data/old.csv','text/csv',1234,42,3,NOW())");

            // documented path: baseline at V1, then apply only the additive V2–V4
            var result = flyway(ds, true).migrate();
            assertThat(result.migrationsExecuted).as("legacy DB applies only the additive V2–V4").isEqualTo(3);
            assertThat(scalar(ds, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL"))
                    .as("baseline plus V2–V4 are recorded").isEqualTo(4);

            // existing data preserved
            assertThat(scalar(ds, "SELECT COUNT(*) FROM users")).isEqualTo(1);
            assertThat(scalar(ds, "SELECT COUNT(*) FROM datasets")).isEqualTo(1);

            // legacy user's BCrypt hash carried over to password_hash
            assertThat(scalar(ds, "SELECT COUNT(*) FROM users WHERE password_hash LIKE '$2a$10$%'"))
                    .as("legacy password hash backfilled").isEqualTo(1);

            // current entity columns that Hibernate cannot safely add itself are
            // present and populated
            assertThat(scalar(ds, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='" + db
                    + "' AND table_name='users' AND column_name='created_at'")).isEqualTo(1);
            assertThat(scalar(ds, "SELECT COUNT(*) FROM users WHERE created_at IS NULL")).isZero();
            assertThat(scalar(ds, "SELECT COUNT(*) FROM datasets WHERE created_at IS NULL OR version < 1")).isZero();

            // legacy NOT NULL columns the current entity no longer writes were
            // relaxed (so inserts succeed) but kept (data preserved)
            assertThat(scalar(ds, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='" + db
                    + "' AND table_name='users' AND column_name='password' AND is_nullable='YES'")).isEqualTo(1);
            assertThat(scalar(ds, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='" + db
                    + "' AND table_name='datasets' AND column_name='file_type' AND is_nullable='YES'")).isEqualTo(1);

            // a new insert in the current shape must now succeed
            exec(ds, "INSERT INTO users (email,name,password_hash,role,created_at) VALUES "
                    + "('new@example.com','New','$2a$10$zzzzzzzzzzzzzzzzzzzzzzABCDEFGHIJKLMNOPQRSTUVWXYZ012345','USER',NOW())");
            assertThat(scalar(ds, "SELECT COUNT(*) FROM users")).isEqualTo(2);

            // idempotent: re-running migrate() is a no-op
            assertThat(flyway(ds, true).migrate().migrationsExecuted).isZero();
            validateWithApplication(schemaUrl);
        } finally {
            if (created) exec(root, "DROP DATABASE " + db);
        }
    }
}
