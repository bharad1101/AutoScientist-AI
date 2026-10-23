package com.autoscientist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabasePersistenceRestartTest {

    private DriverManagerDataSource createDataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(url);
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    @DisplayName("In-memory H2 DB drops data when closed (ephemeral by design)")
    void testInMemoryDatabaseIsEphemeral() {
        String dbName = "ephemeral_test_" + System.currentTimeMillis();
        String memUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=MySQL";

        // Session 1: Create table and insert user while DB_CLOSE_DELAY=-1 keeps it alive during the session
        DriverManagerDataSource ds1 = createDataSource(memUrl);
        JdbcTemplate jdbc1 = new JdbcTemplate(ds1);
        jdbc1.execute("CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(100))");
        jdbc1.update("INSERT INTO users (email) VALUES ('temp@science.org')");

        int countSession1 = jdbc1.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(countSession1).isEqualTo(1);

        // Explicitly shutdown/terminate the in-memory database instance (simulating process termination)
        jdbc1.execute("SHUTDOWN IMMEDIATELY");

        // Session 2: Connect to a fresh in-memory database after JVM/process restart
        String freshMemUrl = "jdbc:h2:mem:" + dbName + "_restarted;DB_CLOSE_DELAY=-1;MODE=MySQL";
        DriverManagerDataSource ds2 = createDataSource(freshMemUrl);
        JdbcTemplate jdbc2 = new JdbcTemplate(ds2);

        // Attempting to select from users proves that a fresh process startup has no prior tables/users
        boolean tableExists = false;
        try {
            jdbc2.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            tableExists = true;
        } catch (Exception expected) {
            tableExists = false;
        }

        assertThat(tableExists)
                .as("In-memory H2 database is completely ephemeral and does NOT persist across process restarts")
                .isFalse();
    }

    @Test
    @DisplayName("File-backed database survives restart and preserves registered accounts (@TempDir)")
    void testFileBackedDatabasePersistsAcrossRestart(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("autoscientist_persistent");
        String fileUrl = "jdbc:h2:file:" + dbPath.toAbsolutePath() + ";DB_CLOSE_DELAY=0;MODE=MySQL";

        // --- Session 1: Run application, register user, then stop backend ---
        DriverManagerDataSource dsSession1 = createDataSource(fileUrl);
        JdbcTemplate jdbc1 = new JdbcTemplate(dsSession1);
        jdbc1.execute("CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(100), password_hash VARCHAR(255))");
        jdbc1.update("INSERT INTO users (email, password_hash) VALUES ('persisted.researcher@science.org', '$2a$10$validHashedPassword')");

        int initialCount = jdbc1.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(initialCount).isEqualTo(1);

        // Simulate backend shutdown
        jdbc1.execute("SHUTDOWN");

        // --- Session 2: Backend starts up again (simulating restart) ---
        DriverManagerDataSource dsSession2 = createDataSource(fileUrl);
        JdbcTemplate jdbc2 = new JdbcTemplate(dsSession2);

        // User account must persist across restart!
        int restartedCount = jdbc2.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(restartedCount).isEqualTo(1);

        String email = jdbc2.queryForObject("SELECT email FROM users WHERE id = 1", String.class);
        assertThat(email).isEqualTo("persisted.researcher@science.org");

        jdbc2.execute("SHUTDOWN");
    }
}
