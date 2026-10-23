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
