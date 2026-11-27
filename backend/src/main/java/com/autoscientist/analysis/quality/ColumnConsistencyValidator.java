package com.autoscientist.analysis.quality;

import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.entity.DataQualityIssue;
import com.autoscientist.entity.DataQualityReport;
import java.util.*;

/**
 * Additional validator that checks column‑wise consistency rules not covered by the main engine.
 * It is deliberately additive – it only adds issues, never removes existing ones.
 */
import org.springframework.stereotype.Component;

@Component
public class ColumnConsistencyValidator {


    /**
     * Validate that a column contains a consistent data type, reasonable range, and is not low‑variance.
     *
     * @param col   the {@link DatasetColumn} metadata (including basic stats)
     * @param report the {@link DataQualityReport} to which new issues will be attached
     */
    public void validate(DatasetColumn col, DataQualityReport report) {
        List<DataQualityIssue> issues = report.getIssues();
        // 1. Consistent type – the profiling step already determines a type, but we can surface a warning
        //    if the column contains mixed numeric vs non‑numeric values.
        if (col.getDataType() == null || col.getDataType().isBlank()) {
            issues.add(issue(report, col.getColumnName(), "INCONSISTENT_TYPE", "WARNING",
                    "Column type could not be determined; mixed or malformed values present.",
                    0));
        }

        // 2. Reasonable range – for numeric columns flag extreme min/max that exceed +/-1e6 (arbitrary safe guard).
        if ("NUMERIC".equalsIgnoreCase(col.getDataType())) {
            Double min = col.getMinVal();
            Double max = col.getMaxVal();
            if (min != null && Math.abs(min) > 1_000_000) {
                issues.add(issue(report, col.getColumnName(), "EXCESSIVE_MIN", "WARNING",
                        String.format("Numeric column %s has an extreme minimum value %.2f", col.getColumnName(), min),
                        0));
            }
            if (max != null && Math.abs(max) > 1_000_000) {
                issues.add(issue(report, col.getColumnName(), "EXCESSIVE_MAX", "WARNING",
                        String.format("Numeric column %s has an extreme maximum value %.2f", col.getColumnName(), max),
                        0));
            }
        }

        // 3. Low variance – if >90% of rows share the same value, issue a warning.
        if (col.getUniqueCount() != null && col.getDataset() != null && col.getDataset().getRowCount() != null && col.getDataset().getRowCount() > 0) {
            double distinctRatio = (double) col.getUniqueCount() / col.getDataset().getRowCount();
            if (distinctRatio < 0.10) { // >90% same value
                issues.add(issue(report, col.getColumnName(), "LOW_VARIANCE", "INFO",
                        String.format("Column %s has low variance (%.1f%% distinct).", col.getColumnName(), distinctRatio * 100),
                        0));
            }
        }
    }

    private DataQualityIssue issue(DataQualityReport report, String column, String type, String severity, String description, int affectedRows) {
        return DataQualityIssue.builder()
                .report(report)
                .columnName(column)
                .issueType(type)
                .severity(severity)
                .description(description)
                .recommendation("Review column definition; consider exclusion or transformation.")
                .affectedRows(affectedRows)
                .build();
    }
}
