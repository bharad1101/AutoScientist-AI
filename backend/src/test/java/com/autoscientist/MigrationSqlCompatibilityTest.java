package com.autoscientist;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards Flyway migrations against SQL that H2 (MODE=MySQL) accepts but real
 * MySQL rejects. The V1 baseline once shipped `CREATE INDEX IF NOT EXISTS`,
 * which passes the H2-based {@link MysqlBaselineMigrationTest} proxy yet fails
 * on MySQL with error 1064 — breaking every fresh install. This test fails the
 * build if such constructs reappear.
 */
class MigrationSqlCompatibilityTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    /** constructs valid on H2 but invalid on MySQL 8+ */
    private static final List<String> MYSQL_INVALID = List.of(
            "CREATE INDEX IF NOT EXISTS",
            "DROP INDEX IF EXISTS",
            "CREATE UNIQUE INDEX IF NOT EXISTS");

    @Test
    void migrationsAvoidConstructsRejectedByRealMysql() throws IOException {
        assertThat(MIGRATIONS).as("classpath migration directory must exist").exists();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String sql = stripComments(Files.readString(file)).toUpperCase();
                for (String construct : MYSQL_INVALID) {
                    if (sql.contains(construct.toUpperCase())) {
                        violations.add(file.getFileName() + " contains '" + construct
                                + "' (invalid on MySQL; use an information_schema-guarded PREPARE instead)");
                    }
                }
            }
        }

        assertThat(violations).as("MySQL-incompatible SQL in db/migration").isEmpty();
    }

    @Test
    void migrationVersionsAreOrderedAndUnique() throws IOException {
        List<Integer> versions = new ArrayList<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String name = file.getFileName().toString();
                assertThat(name).matches("V\\d+__.*\\.sql");
                versions.add(Integer.parseInt(name.substring(1, name.indexOf("__"))));
            }
        }

        versions.sort(Integer::compareTo);
        assertThat(versions).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void mysqlOnlyMigrationsAreExplicitlyDocumented() throws IOException {
        String v5 = Files.readString(MIGRATIONS.resolve("V5__jobs_and_model_artifacts.sql"));
        assertThat(v5).doesNotContain("information_schema");

        for (String name : new String[]{
                "V2__provenance_delta.sql", "V3__association_confidence_intervals.sql",
                "V6__model_artifact_longblob.sql", "V7__job_ownership_and_parameters.sql"}) {
            String sql = Files.readString(MIGRATIONS.resolve(name));
            assertThat(sql).as(name + " must guard MySQL-only DDL")
                    .contains("information_schema")
                    .contains("PREPARE");
        }
    }

    @Test
    void v6GuardHandlesMissingAndAlreadyWideArtifactTables() throws IOException {
        String v6 = Files.readString(MIGRATIONS.resolve("V6__model_artifact_longblob.sql"));
        assertThat(v6).contains("information_schema.TABLES")
                .contains("information_schema.COLUMNS")
                .contains("COLUMN_TYPE <> 'longblob'")
                .contains("MODIFY COLUMN payload LONGBLOB NOT NULL");
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        for (String line : sql.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
