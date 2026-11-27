package com.autoscientist;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFlywayConsistencyTest {

    private static final Path SCHEMA_PATH = Path.of("..", "database", "schema.sql");
    private static final Path MIGRATIONS_PATH = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z_][a-z0-9_]*)\\s*\\((.*?)\\)\\s*(?:engine\\s*=\\s*\\w+)?\\s*;",
            Pattern.DOTALL);
    private static final Set<String> NON_COLUMN_DECLARATIONS = Set.of(
            "constraint", "index", "key", "unique", "primary", "foreign", "check");

    @Test
    void referenceSchemaMatchesAuthoritativeFlywayTablesAndColumns() throws IOException {
        Map<String, Set<String>> migrations = parseMigrationTables();
        Map<String, Set<String>> reference = parseTables(Files.readString(SCHEMA_PATH).toLowerCase(Locale.ROOT));

        assertThat(reference.keySet())
                .as("database/schema.sql tables must match the authoritative Flyway migrations")
                .containsExactlyInAnyOrderElementsOf(migrations.keySet());

        migrations.forEach((table, columns) ->
                assertThat(reference.get(table))
                        .as("columns for %s must match the authoritative Flyway migrations", table)
                        .containsExactlyInAnyOrderElementsOf(columns));
    }

    @Test
    void referenceSchemaUsesAuthoritativeFindingsAndAnalysisRunColumns() throws IOException {
        Map<String, Set<String>> migrations = parseMigrationTables();
        Map<String, Set<String>> reference = parseTables(Files.readString(SCHEMA_PATH).toLowerCase(Locale.ROOT));

        assertThat(migrations.get("findings")).contains("model_id");
        assertThat(reference.get("findings")).contains("model_id");
        assertThat(reference.get("analysis_runs"))
                .as("analysis_runs has no legacy job_id/model_name/model_path columns")
                .doesNotContain("job_id", "model_name", "model_path");
    }

    private static Map<String, Set<String>> parseMigrationTables() throws IOException {
        Map<String, Set<String>> tables = new TreeMap<>();
        try (Stream<Path> paths = Files.list(MIGRATIONS_PATH)) {
            List<Path> migrations = paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
            assertThat(migrations).as("Flyway migrations must be present").isNotEmpty();
            for (Path migration : migrations) {
                merge(tables, parseTables(Files.readString(migration).toLowerCase(Locale.ROOT)));
            }
        }
        return tables;
    }

    private static void merge(Map<String, Set<String>> target, Map<String, Set<String>> source) {
        source.forEach((table, columns) ->
                target.computeIfAbsent(table, ignored -> new TreeSet<>()).addAll(columns));
    }

    private static Map<String, Set<String>> parseTables(String sqlLower) {
        Map<String, Set<String>> tables = new TreeMap<>();
        Matcher matcher = CREATE_TABLE.matcher(sqlLower);
        while (matcher.find()) {
            tables.computeIfAbsent(matcher.group(1), ignored -> new TreeSet<>())
                    .addAll(parseColumns(matcher.group(2)));
        }
        return tables;
    }

    private static Set<String> parseColumns(String blockLower) {
        Set<String> columns = new TreeSet<>();
        for (String rawLine : blockLower.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            String firstToken = line.split("\\s+", 2)[0];
            if (!NON_COLUMN_DECLARATIONS.contains(firstToken)) {
                columns.add(firstToken.replace(",", ""));
            }
        }
        return columns;
    }
}
