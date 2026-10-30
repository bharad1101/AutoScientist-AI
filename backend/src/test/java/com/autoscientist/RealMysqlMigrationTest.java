package com.autoscientist;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-MySQL migration integration test. Runs only when {@code AS_TEST_MYSQL_URL}
 * (and {@code AS_TEST_MYSQL_USER}/{@code AS_TEST_MYSQL_PASSWORD}) are set, so the
 * normal {@code ./mvnw test} build never depends on a database. It uses only
 * throwaway schemas and never touches an existing database's tables.
 *
 * <p>H2 in MODE=MySQL is a useful proxy but is NOT sufficient: it accepts SQL
 * MySQL rejects ({@code CREATE INDEX IF NOT EXISTS}) and resolves table
 * references differently at PREPARE time. Both bugs were caught here and not on
 * H2. This test therefore exercises the two real paths on real MySQL:
 *
 * <ol>
 *   <li><b>fresh install</b> — empty schema, Flyway V1–V4, then Hibernate
 *       {@code ddl-auto=validate} must find the schema complete.</li>
 *   <li><b>legacy pre-Flyway upgrade</b> — a minimised copy of the historical
 *       schema (narrower tables, {@code users.password} NOT NULL,
 *       {@code datasets.file_type}/{@code file_path}/{@code uploaded_at} NOT
 *       NULL, no {@code created_at}) with a live row; Flyway baselines at V1 and
 *       applies V2–V4, after which the legacy rows survive and the schema
 *       satisfies every current entity column the application writes.</li>
 * </ol>
 *
 * Run it with, for example:
 * <pre>
 *   AS_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/' \
 *   AS_TEST_MYSQL_USER=root AS_TEST_MYSQL_PASSWORD=secret \
 *   ./mvnw -Dtest=RealMysqlMigrationTest test
 * </pre>
 * The URL must end at the server (no database) so this test can create and drop
 * its own schemas.
 */
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_URL", matches = ".+")
class RealMysqlMigrationTest {

    private static final String URL = System.getenv("AS_TEST_MYSQL_URL");
    private static final String USER = System.getenv().getOrDefault("AS_TEST_MYSQL_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("AS_TEST_MYSQL_PASSWORD", "");

    private DataSource dataSource(String database) {
        org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(URL + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
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

    @Test
    void freshInstallMigratesAndMatchesEntities() throws Exception {
        String db = "as_it_fresh";
        DataSource root = dataSource("");
        exec(root, "DROP DATABASE IF EXISTS " + db, "CREATE DATABASE " + db + " CHARACTER SET utf8mb4");
        DataSource ds = dataSource(db);
        try {
            var result = flyway(ds, false).migrate();
            assertThat(result.migrationsExecuted).as("fresh install runs V1–V4").isEqualTo(4);
            // every table the entities require must exist
            assertThat(scalar(ds, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema='" + db + "' AND table_name='analysis_runs'")).isEqualTo(1);
            assertThat(scalar(ds, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema='" + db + "' AND table_name='drift_snapshots'")).isEqualTo(1);
        } finally {
            exec(root, "DROP DATABASE IF EXISTS " + db);
        }
    }

    @Test
    void legacyPreFlywayDatabaseUpgradesWithoutDataLoss() throws Exception {
        String db = "as_it_legacy";
        DataSource root = dataSource("");
        exec(root, "DROP DATABASE IF EXISTS " + db, "CREATE DATABASE " + db + " CHARACTER SET utf8mb4");
        DataSource ds = dataSource(db);
        try {
            // minimised legacy schema: only the columns that historically existed
            // (users.password NOT NULL, datasets.file_type/file_path/uploaded_at
            // NOT NULL, no created_at on users/datasets)
            exec(ds,
                    "CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(255) NOT NULL, "
                            + "name VARCHAR(255) NOT NULL, password VARCHAR(255) NOT NULL, "
                            + "role ENUM('ADMIN','USER') NOT NULL, UNIQUE KEY uk_users_email (email))",
                    "CREATE TABLE datasets (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, "
                            + "original_filename VARCHAR(255) NOT NULL, stored_filename VARCHAR(255) NOT NULL, "
                            + "file_path VARCHAR(1024) NOT NULL, file_type VARCHAR(255) NOT NULL, "
                            + "file_size BIGINT NOT NULL, row_count BIGINT NULL, column_count INT NULL, "
                            + "column_names TEXT NULL, uploaded_at DATETIME(6) NOT NULL)",
                    "INSERT INTO users (email,name,password,role) VALUES "
                            + "('legacy@example.com','Legacy','$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345','USER')",
                    "INSERT INTO datasets (user_id,original_filename,stored_filename,file_path,file_type,file_size,"
                            + "row_count,column_count,uploaded_at) VALUES "
                            + "(1,'old.csv','old-uuid.csv','/data/old.csv','text/csv',1234,42,3,NOW())");

            // documented path: baseline at V1, then apply only the additive V2–V4
            var result = flyway(ds, true).migrate();
            assertThat(result.migrationsExecuted).as("legacy DB applies only the additive V2–V4").isEqualTo(3);

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
        } finally {
            exec(root, "DROP DATABASE IF EXISTS " + db);
        }
    }
}
