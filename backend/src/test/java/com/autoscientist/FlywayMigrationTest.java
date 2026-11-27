package com.autoscientist;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the supported Flyway entry paths on H2 in MySQL mode:
 *
 * <ol>
 *   <li><b>Fresh database</b> — V1 (the committed 17-table baseline) is applied
 *       exactly once.</li>
 *   <li><b>Existing pre-Flyway database</b> — the schema is baselined at V1, so
 *       V1 is never re-executed and the existing jobs table/data is preserved.
 *       V2/V3/V7 are MySQL-only, so this test caps the H2 run at V1.</li>
 *   <li><b>V5 portability</b> — the real V5 script is executed on H2 to prove
 *       the new jobs/model_artifacts DDL is valid outside MySQL.</li>
 * </ol>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flyway_migration_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private DataSource dataSource;

    private Flyway flywayAtV1(boolean baseline) {
        return Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .baselineOnMigrate(baseline)
                .baselineVersion("1")
                .target("1")
                .load();
    }

    @Test
    void freshDatabaseAppliesBaselineOnce() {
        Flyway flyway = flywayAtV1(false);
        flyway.clean();
        flyway.migrate();

        var applied = flyway.info().applied();
        assertEquals(1, applied.length, "Expected exactly the V1 baseline to be applied");
        assertEquals("1", applied[0].getVersion().getVersion(), "First migration should be V1");

        assertDoesNotThrow(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM users LIMIT 1")) {
                rs.getMetaData();
            }
        });
    }

    @Test
    void existingDatabaseIsBaselinedAtV1AndPreservesJobsTable() throws Exception {
        Flyway flyway = flywayAtV1(true);
        flyway.clean();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE jobs (" +
                            "    id CHAR(36) PRIMARY KEY," +
                            "    type VARCHAR(255) NOT NULL," +
                            "    status VARCHAR(20) NOT NULL," +
                            "    progress INT NOT NULL," +
                            "    error_message TEXT," +
                            "    created_at TIMESTAMP NOT NULL," +
                            "    started_at TIMESTAMP NULL," +
                            "    completed_at TIMESTAMP NULL," +
                            "    transient_file_path VARCHAR(255)" +
                            ")");
            stmt.executeUpdate("INSERT INTO jobs (id, type, status, progress, created_at) "
                    + "VALUES (RANDOM_UUID(), 'EDA', 'COMPLETED', 100, CURRENT_TIMESTAMP)");
        }

        flyway.baseline();
        flyway.migrate();

        var applied = flyway.info().applied();
        assertEquals(1, applied.length, "Baseline plus migrations should contain only the V1 marker");
        assertEquals("1", applied[0].getVersion().getVersion(), "Baseline version should be 1");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM jobs")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "Pre-existing job data must be preserved");
        }
    }

    @Test
    void v5CreatesJobsAndModelArtifactsOnH2() throws Exception {
        Flyway flyway = flywayAtV1(false);
        flyway.clean();
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/migration/V5__jobs_and_model_artifacts.sql"));

            for (String column : new String[]{"owner_id", "dataset_id", "target_column", "problem_type"}) {
                try (ResultSet rs = conn.getMetaData().getColumns(null, null, "JOBS", column.toUpperCase())) {
                    assertTrue(rs.next(), "jobs." + column + " must exist after V5");
                }
            }
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "MODEL_ARTIFACTS", "PAYLOAD")) {
                assertTrue(rs.next(), "model_artifacts.payload must exist after V5");
            }
            try (ResultSet rs = conn.getMetaData().getImportedKeys(null, null, "MODEL_ARTIFACTS")) {
                boolean found = false;
                while (rs.next()) {
                    if ("EXPERIMENT_VERSIONS".equalsIgnoreCase(rs.getString("PKTABLE_NAME"))) {
                        found = true;
                    }
                }
                assertTrue(found, "model_artifacts must keep the FK that DatasetService deletion respects");
            }
        }
    }
}
