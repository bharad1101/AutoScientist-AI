package com.autoscientist.analysis.quality;

import com.autoscientist.entity.DataQualityIssue;
import com.autoscientist.entity.DataQualityReport;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.autoscientist.util.CsvTable;

/**
 * Engine that evaluates data quality for a dataset.
 * Includes core checks and delegates additional column consistency validation.
 */
@Component
public class DataQualityEngine {

    private final ColumnConsistencyValidator consistencyValidator;

    // No‑arg constructor for legacy tests
    public DataQualityEngine() {
        this.consistencyValidator = new ColumnConsistencyValidator();
    }

    // Primary constructor for production use
    @Autowired
    public DataQualityEngine(ColumnConsistencyValidator consistencyValidator) {
        this.consistencyValidator = consistencyValidator;
    }

    public DataQualityReport evaluate(InputStream inputStream, Dataset dataset, List<DatasetColumn> columns) throws Exception {
        return evaluate(CsvTable.read(inputStream), dataset, columns);
    }

    public DataQualityReport evaluate(CsvTable table, Dataset dataset, List<DatasetColumn> columns) throws Exception {
        List<DataQualityIssue> issues = new ArrayList<>();
        int totalRows = table.getRowCount();

        DataQualityReport report = DataQualityReport.builder()
                .dataset(dataset)
                .issues(issues)
                .build();

        // 1. Duplicate rows
        int duplicateRowCount = table.getDuplicateRowCount();
        double duplicateScore = totalRows > 0 ? Math.max(0.0, 100.0 - ((double) duplicateRowCount / totalRows * 100.0)) : 100.0;
        if (duplicateRowCount > 0) {
            issues.add(DataQualityIssue.builder()
                    .report(report)
                    .columnName(null)
                    .issueType("DUPLICATE_ROWS")
                    .severity(duplicateRowCount > totalRows * 0.10 ? "WARNING" : "INFO")
                    .description(String.format("Found %d duplicate rows (%.2f%% of total rows).", duplicateRowCount, (double) duplicateRowCount / totalRows * 100.0))
                    .recommendation("Consider deduplicating records prior to statistical modeling to avoid bias.")
                    .affectedRows(duplicateRowCount)
                    .build());
        }

        // 2. Column‑level audits
        double totalMissingPctSum = 0;
        int outlierCountSum = 0;
        int inconsistentCategoriesCount = 0;

        for (DatasetColumn col : columns) {
            totalMissingPctSum += col.getMissingPct();

            // Missingness checks
            if (col.getMissingPct() > 50.0) {
                issues.add(DataQualityIssue.builder()
                        .report(report)
                        .columnName(col.getColumnName())
                        .issueType("CRITICAL_MISSINGNESS")
                        .severity("ERROR")
                        .description(String.format("Column '%s' has %.1f%% missing values.", col.getColumnName(), col.getMissingPct()))
                        .recommendation("Imputation may introduce heavy bias. Consider excluding or obtaining complete data.")
                        .affectedRows(col.getMissingCount())
                        .build());
            } else if (col.getMissingPct() > 15.0) {
                issues.add(DataQualityIssue.builder()
                        .report(report)
                        .columnName(col.getColumnName())
                        .issueType("HIGH_MISSINGNESS")
                        .severity("WARNING")
                        .description(String.format("Column '%s' has %.1f%% missing values.", col.getColumnName(), col.getMissingPct()))
                        .recommendation("Use robust imputation (median for numeric, mode for categorical) or missingness indicators.")
                        .affectedRows(col.getMissingCount())
                        .build());
            } else if (col.getMissingCount() != null && col.getMissingCount() > 0) {
                issues.add(DataQualityIssue.builder()
                        .report(report)
                        .columnName(col.getColumnName())
                        .issueType("LOW_MISSINGNESS")
                        .severity("INFO")
                        .description(String.format("Column '%s' has %d missing values (%.1f%%).", col.getColumnName(), col.getMissingCount(), col.getMissingPct()))
                        .recommendation("Paired complete-case analysis or mean/median imputation is suitable.")
                        .affectedRows(col.getMissingCount())
                        .build());
            }

            // Zero variance
            if (col.getUniqueCount() != null && col.getUniqueCount() <= 1 && totalRows > 1) {
                issues.add(DataQualityIssue.builder()
                        .report(report)
                        .columnName(col.getColumnName())
                        .issueType("ZERO_VARIANCE")
                        .severity("WARNING")
                        .description(String.format("Column '%s' has constant values (0 variance).", col.getColumnName()))
                        .recommendation("Constant columns provide no explanatory power in statistical tests or ML. Safe to drop.")
                        .affectedRows(totalRows)
                        .build());
            }

            // Unique identifier
            if (col.getUniqueCount() != null && col.getUniqueCount() == totalRows && totalRows > 10 &&
                    ("TEXT".equals(col.getDataType()) || "NUMERIC".equals(col.getDataType()))) {
                issues.add(DataQualityIssue.builder()
                        .report(report)
                        .columnName(col.getColumnName())
                        .issueType("UNIQUE_IDENTIFIER")
                        .severity("INFO")
                        .description(String.format("Column '%s' has 100%% distinct values; appears to be an ID/key.", col.getColumnName()))
                        .recommendation("Exclude identifiers from statistical modeling to prevent spurious overfitting.")
                        .affectedRows(totalRows)
                        .build());
            }

            // Outlier detection (IQR)
            if ("NUMERIC".equals(col.getDataType()) && col.getQ1() != null && col.getQ3() != null) {
                double iqr = col.getQ3() - col.getQ1();
                if (iqr > 0) {
                    double lower = col.getQ1() - 1.5 * iqr;
                    double upper = col.getQ3() + 1.5 * iqr;
                    int colIndex = col.getColumnIndex();
                    int outliers = 0;
                    for (String raw : table.getColumn(colIndex)) {
                        try {
                            double v = Double.parseDouble(raw);
                            if (v < lower || v > upper) outliers++;
                        } catch (Exception ignored) {}
                    }
                    if (outliers > 0) {
                        outlierCountSum += outliers;
                        issues.add(DataQualityIssue.builder()
                                .report(report)
                                .columnName(col.getColumnName())
                                .issueType("OUTLIERS_DETECTED")
                                .severity("INFO")
                                .description(String.format("Column '%s' has %d potential statistical outliers (beyond 1.5x IQR).", col.getColumnName(), outliers))
                                .recommendation("Verify if outliers represent legitimate extreme observations or measurement errors.")
                                .affectedRows(outliers)
                                .build());
                    }
                }
            }

            // Inconsistent casing for categorical columns
            if ("CATEGORICAL".equals(col.getDataType())) {
                int colIndex = col.getColumnIndex();
                java.util.Map<String, java.util.Set<String>> lowerToOriginals = new java.util.HashMap<>();
                for (String val : table.getColumn(colIndex)) {
                    if (val != null && !val.trim().isEmpty()) {
                        String lower = val.trim().toLowerCase();
                        lowerToOriginals.computeIfAbsent(lower, k -> new java.util.HashSet<>()).add(val.trim());
                    }
                }
                for (java.util.Map.Entry<String, java.util.Set<String>> e : lowerToOriginals.entrySet()) {
                    if (e.getValue().size() > 1) {
                        inconsistentCategoriesCount++;
                        issues.add(DataQualityIssue.builder()
                                .report(report)
                                .columnName(col.getColumnName())
                                .issueType("INCONSISTENT_CASING")
                                .severity("WARNING")
                                .description(String.format("Column '%s' contains casing inconsistencies for value '%s': %s.", col.getColumnName(), e.getKey(), e.getValue()))
                                .recommendation("Standardize category strings (e.g. normalize to lowercase/uppercase).")
                                .affectedRows(e.getValue().size())
                                .build());
                        break;
                    }
                }
            }
        }

        // 3. Explicit value-level checks: invalid data types, inconsistent formats,
        //    and cross-column redundancy. These are additive and never drop rows.
        int invalidValueCount = 0;
        int formatIssueCount = 0;
        for (DatasetColumn col : columns) {
            int colIndex = col.getColumnIndex();
            if (colIndex < 0 || colIndex >= table.getColumnCount()) {
                continue;
            }
            List<String> values = table.getColumn(colIndex);
            if ("NUMERIC".equals(col.getDataType())) {
                int invalid = 0;
                int commaDecimals = 0;
                int dotDecimals = 0;
                for (String raw : values) {
                    if (isMissingToken(raw)) {
                        continue;
                    }
                    String value = raw.trim();
                    if (value.matches(".*\\d,\\d.*")) commaDecimals++;
                    if (value.matches(".*\\d\\.\\d.*")) dotDecimals++;
                    try {
                        Double.parseDouble(value.replace(",", "."));
                    } catch (NumberFormatException e) {
                        invalid++;
                    }
                }
                if (invalid > 0) {
                    invalidValueCount += invalid;
                    issues.add(DataQualityIssue.builder()
                            .report(report)
                            .columnName(col.getColumnName())
                            .issueType("INVALID_DATA_TYPE")
                            .severity("WARNING")
                            .description(String.format(
                                    "Column '%s' is profiled as numeric but contains %d non-numeric value(s).",
                                    col.getColumnName(), invalid))
                            .recommendation("Correct or remove malformed values; mixed types break numeric analysis.")
                            .affectedRows(invalid)
                            .build());
                }
                if (commaDecimals > 0 && dotDecimals > 0) {
                    formatIssueCount++;
                    issues.add(DataQualityIssue.builder()
                            .report(report)
                            .columnName(col.getColumnName())
                            .issueType("INCONSISTENT_NUMERIC_FORMAT")
                            .severity("WARNING")
                            .description(String.format(
                                    "Column '%s' mixes comma and dot decimal separators.",
                                    col.getColumnName()))
                            .recommendation("Standardize on one decimal separator before analysis.")
                            .affectedRows(commaDecimals + dotDecimals)
                            .build());
                }
            } else if ("DATE_TIME".equals(col.getDataType())) {
                int invalidDates = 0;
                for (String raw : values) {
                    if (isMissingToken(raw)) {
                        continue;
                    }
                    if (!looksLikeDate(raw.trim())) {
                        invalidDates++;
                    }
                }
                if (invalidDates > 0) {
                    invalidValueCount += invalidDates;
                    formatIssueCount++;
                    issues.add(DataQualityIssue.builder()
                            .report(report)
                            .columnName(col.getColumnName())
                            .issueType("INCONSISTENT_DATE_FORMAT")
                            .severity("WARNING")
                            .description(String.format(
                                    "Column '%s' is profiled as a date/time but contains %d value(s) "
                                            + "in an unrecognised format.",
                                    col.getColumnName(), invalidDates))
                            .recommendation("Normalize dates to a single ISO-8601 format.")
                            .affectedRows(invalidDates)
                            .build());
                }
            }
        }

        // Cross-column redundancy: identical value vectors are a consistency risk
        // (double counting, accidental duplication of a predictor).
        int duplicateColumnCount = detectDuplicateColumns(table, columns, report);

        // Apply additional column consistency validator
        if (consistencyValidator != null) {
            columns.forEach(col -> consistencyValidator.validate(col, report));
        }

        // Score calculations (explainable, each component 0–100)
        int numCols = columns.isEmpty() ? 1 : columns.size();
        double avgMissingPct = totalMissingPctSum / numCols;
        double missingValueScore = Math.max(0.0, 100.0 - avgMissingPct * 1.5);
        double outlierFraction = totalRows > 0 ? (double) outlierCountSum / (totalRows * numCols) : 0.0;
        double outlierScore = Math.max(0.0, 100.0 - (outlierFraction * 100.0 * 2.0));
        double invalidFraction = totalRows > 0 ? (double) invalidValueCount / (totalRows * numCols) : 0.0;
        double consistencyScore = Math.max(0.0, 100.0
                - (inconsistentCategoriesCount * 15.0)
                - (formatIssueCount * 10.0)
                - (duplicateColumnCount * 10.0)
                - (invalidFraction * 100.0));

        double overallScore = (missingValueScore * 0.40) + (duplicateScore * 0.25) +
                (outlierScore * 0.15) + (consistencyScore * 0.20);

        // Set score fields on report
        report.setOverallScore(overallScore);
        report.setMissingValueScore(missingValueScore);
        report.setDuplicateScore(duplicateScore);
        report.setOutlierScore(outlierScore);
        report.setConsistencyScore(consistencyScore);
        report.setTotalIssues(issues.size());
        return report;
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

    private static boolean isMissingToken(String raw) {
        if (raw == null) {
            return true;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return value.isEmpty()
                || value.equals("na")
                || value.equals("n/a")
                || value.equals("null")
                || value.equals("nan")
                || value.equals("none");
    }

    private static boolean looksLikeDate(String value) {
        if (value == null || value.length() < 8) {
            return false;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDate.parse(value, format);
                return true;
            } catch (Exception ignored) {
            }
            try {
                LocalDateTime.parse(value, format);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Flags columns whose value vectors are identical. Returns the number of
     * duplicate columns found so the consistency score can account for them.
     */
    private int detectDuplicateColumns(CsvTable table, List<DatasetColumn> columns, DataQualityReport report) {
        Map<String, Integer> firstIndexBySignature = new HashMap<>();
        int duplicates = 0;
        for (DatasetColumn col : columns) {
            int colIndex = col.getColumnIndex();
            if (colIndex < 0 || colIndex >= table.getColumnCount()) {
                continue;
            }
            List<String> values = table.getColumn(colIndex);
            if (values.isEmpty()) {
                continue;
            }
            boolean hasValue = false;
            for (String value : values) {
                if (!isMissingToken(value)) {
                    hasValue = true;
                    break;
                }
            }
            if (!hasValue) {
                continue;
            }
            String signature = values.size() + ":" + values.hashCode();
            Integer first = firstIndexBySignature.get(signature);
            if (first == null) {
                firstIndexBySignature.put(signature, colIndex);
                continue;
            }
            if (table.getColumn(first).equals(values)) {
                duplicates++;
                report.getIssues().add(DataQualityIssue.builder()
                        .report(report)
                        .columnName(col.getColumnName())
                        .issueType("DUPLICATE_COLUMN")
                        .severity("INFO")
                        .description(String.format(
                                "Column '%s' is identical to an earlier column; redundant predictors "
                                        + "can inflate feature importance and distort tests.",
                                col.getColumnName()))
                        .recommendation("Drop one of the identical columns from modelling.")
                        .affectedRows(values.size())
                        .build());
            }
        }
        return duplicates;
    }
}
