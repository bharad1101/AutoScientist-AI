package com.autoscientist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@code database/schema.sql} against drift from the JPA entities.
 *
 * For every {@link Entity} in {@code com.autoscientist.entity} this test
 * asserts that the reference schema contains the table and each explicitly
 * named column ({@link Table}, {@link Column}, {@linkJoinColumn}). A new
 * entity column that is not reflected in {@code database/schema.sql} fails
 * the build until the reference schema is reconciled — the exact drift that
 * previously left {@code analysis_runs}, {@code drift_snapshots} and
 * {@code datasets.source_stored_filename} undocumented for production
 * installs.
 */
class SchemaReferenceSyncTest {

    private static final Path SCHEMA_PATH = Path.of("..", "database", "schema.sql");

    @Test
    void referenceSchemaContainsEveryEntityTableAndColumn() throws IOException {
        assertThat(SCHEMA_PATH)
                .as("database/schema.sql must exist relative to backend/")
                .exists();

        String schema = Files.readString(SCHEMA_PATH).toLowerCase();
        Map<String, Set<String>> entities = scanEntityColumns();

        assertThat(entities).as("entity scan must find the domain model").isNotEmpty();

        Map<String, Set<String>> missing = new LinkedHashMap<>();
        entities.forEach((table, columns) -> {
            String block = extractCreateTableBlock(schema, table);
            if (block == null) {
                missing.put(table + " (TABLE)", columns);
                return;
            }
            Set<String> absent = new TreeSet<>();
            for (String column : columns) {
                if (!columnPresent(block, column)) {
                    absent.add(column);
                }
            }
            if (!absent.isEmpty()) {
                missing.put(table, absent);
            }
        });

        assertThat(missing)
                .as("entities whose tables/columns are absent from database/schema.sql")
                .isEmpty();
    }

    /** table name -> set of physical column names derived from the entity mapping */
    private static Map<String, Set<String>> scanEntityColumns() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.autoscientist.entity")) {
            Class<?> entityClass;
            try {
                entityClass = Class.forName(candidate.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }

            Table table = entityClass.getAnnotation(Table.class);
            assertThat(table).as("%s must declare @Table", entityClass.getSimpleName()).isNotNull();
            String tableName = table.name().toLowerCase();
            Set<String> columns = new TreeSet<>();

            for (var field : entityClass.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                if (joinColumn != null && StringUtils.hasText(joinColumn.name())) {
                    columns.add(joinColumn.name().toLowerCase());
                } else if (column != null) {
                    columns.add(StringUtils.hasText(column.name())
                            ? column.name().toLowerCase()
                            : toSnakeCase(field.getName()));
                }
            }
            result.put(tableName, columns);
        }
        return result;
    }

    private static String extractCreateTableBlock(String schemaLower, String table) {
        Pattern pattern = Pattern.compile(
                "create table if not exists " + Pattern.quote(table)
                        + "\\s*\\((.*?)\\)\\s*(?:engine\\s*=\\s*\\w+)?\\s*;",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(schemaLower);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean columnPresent(String blockLower, String column) {
        return Pattern.compile("(^|[\\s,(])" + Pattern.quote(column) + "([\\s,)]|$)",
                Pattern.MULTILINE).matcher(blockLower).find();
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
