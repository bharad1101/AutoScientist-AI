package com.autoscientist;

import com.autoscientist.analysis.report.PdfExportService;
import com.autoscientist.analysis.report.ReportGeneratorService;
import com.autoscientist.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportAndPdfTest {

    private final ReportGeneratorService reportGeneratorService = new ReportGeneratorService();
    private final PdfExportService pdfExportService = new PdfExportService();

    @Test
    @DisplayName("ReportGeneratorService & PdfExportService: Generates Markdown and valid PDF binary")
    void testReportAndPdfGeneration() {
        DatasetColumn col1 = DatasetColumn.builder()
                .columnName("age")
                .dataType("NUMERIC")
                .typeConfidence(1.0)
                .missingCount(0)
                .missingPct(0.0)
                .uniqueCount(40)
                .meanVal(35.2)
                .medianVal(34.0)
                .stdDev(8.4)
                .minVal(18.0)
                .maxVal(65.0)
                .build();

        Dataset dataset = Dataset.builder()
                .id(1L)
                .name("Health Metrics Cohort")
                .originalFilename("health.csv")
                .rowCount(500)
                .columnCount(1)
                .datasetType("REGRESSION")
                .columns(List.of(col1))
                .build();

        DataQualityReport qualityReport = DataQualityReport.builder()
                .overallScore(94.5)
                .missingValueScore(100.0)
                .duplicateScore(98.0)
                .outlierScore(88.0)
                .consistencyScore(92.0)
                .totalIssues(0)
                .build();

        Finding finding = Finding.builder()
                .id(1L)
                .title("Elevated Biomarker Association with Age")
                .summary("Statistically significant linear correlation observed.")
                .evidenceText("Pearson r=0.48, p=1.2e-5, N=500")
                .confidenceLevel("HIGH")
                .causalityDisclaimer("Observational finding only; confounding unaddressed.")
                .build();

        String markdown = reportGeneratorService.generateScientificReportMarkdown(
                dataset, qualityReport, List.of(), null, List.of(), List.of(), List.of(), List.of(finding));

        assertNotNull(markdown);
        assertTrue(markdown.contains("Comprehensive Empirical Discovery Report"));
        assertTrue(markdown.contains("Health Metrics Cohort"));
        assertTrue(markdown.contains("Data Quality & Hygiene Assessment"));
        assertTrue(markdown.contains("Strict Causality Disclaimer"));
        assertTrue(markdown.contains("Machine Learning: Not performed"));
        assertTrue(markdown.contains("Explainability: Not available"));
        assertTrue(markdown.contains("Fairness audit: not run"));
        assertTrue(markdown.contains("Data drift: not run"));
        assertTrue(markdown.contains("Conclusion & Reproducibility"));
        assertTrue(markdown.contains("Methodological Limitations"));

        // Generate PDF
        byte[] pdfBytes = pdfExportService.generatePdfReport("Scientific Discovery Report", markdown);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF byte array should contain valid PDF structure");

        // Verify PDF Magic Bytes %PDF
        String magic = new String(pdfBytes, 0, 4);
        assertEquals("%PDF", magic);
    }

    @Test
    @DisplayName("Report includes calibration and residual diagnostics when persisted")
    void testReportIncludesModelDiagnostics() {
        Dataset dataset = Dataset.builder()
                .id(2L).name("Diagnostics").originalFilename("d.csv")
                .rowCount(200).columnCount(1).datasetType("CLASSIFICATION")
                .columns(List.of(DatasetColumn.builder().columnName("x").dataType("NUMERIC")
                        .typeConfidence(1.0).missingCount(0).missingPct(0.0).uniqueCount(10).build()))
                .build();

        ModelRun calibrated = ModelRun.builder()
                .modelName("Logistic Regression").algorithm("LOGISTIC_REGRESSION").isBestModel(true)
                .evaluationJson("{\"calibration\":{\"brierScore\":0.1234,\"bins\":[{\"meanPredicted\":0.2,"
                        + "\"observedFrequency\":0.25,\"count\":11}]}}")
                .build();
        ModelRun regressor = ModelRun.builder()
                .modelName("Ridge Regression").algorithm("RIDGE").isBestModel(true)
                .evaluationJson("{\"residualSummary\":{\"meanError\":0.5,\"meanAbsoluteError\":2.5,"
                        + "\"maxAbsoluteError\":9.0}}")
                .build();

        String markdown = reportGeneratorService.generateScientificReportMarkdown(
                dataset, null, List.of(), null, List.of(), List.of(), List.of(calibrated), List.of());
        assertTrue(markdown.contains("Model calibration (reliability)"));
        assertTrue(markdown.contains("Brier score **0.1234**"));
        assertTrue(markdown.contains("| 0.2000 | 0.2500 | 11 |"));

        String regression = reportGeneratorService.generateScientificReportMarkdown(
                dataset, null, List.of(), null, List.of(), List.of(), List.of(regressor), List.of());
        assertTrue(regression.contains("Residual diagnostics"));
        assertTrue(regression.contains("| 0.5000 | 2.5000 | 9.0000 |"));
    }

    @Test
    @DisplayName("Report renders model metrics readably instead of raw JSON blobs")
    void testReportMetricsAreHumanReadable() {
        Dataset dataset = Dataset.builder()
                .id(3L).name("Metrics").originalFilename("m.csv")
                .rowCount(120).columnCount(2).datasetType("CLASSIFICATION")
                .columns(List.of(DatasetColumn.builder().columnName("x").dataType("NUMERIC")
                        .typeConfidence(1.0).missingCount(0).missingPct(0.0).uniqueCount(10).build()))
                .build();

        ModelRun run = ModelRun.builder()
                .modelName("Random Forest").algorithm("RANDOM_FOREST").isBestModel(true)
                .valMetricsJson("{\"accuracy\":0.9123,\"precision\":0.9,\"recall\":0.88,\"f1\":0.8899}")
                .testMetricsJson("{\"accuracy\":0.8765,\"precision\":0.87,\"recall\":0.85,\"f1\":0.8599}")
                .build();

        String markdown = reportGeneratorService.generateScientificReportMarkdown(
                dataset, null, List.of(), null, List.of(), List.of(), List.of(run), List.of());

        assertFalse(markdown.contains("\"accuracy\""), "raw JSON metric keys must not leak into the report");
        assertFalse(markdown.contains("{\"accuracy\""), "raw JSON metric object must not be embedded");
        assertTrue(markdown.contains("acc=0.9123"), "validation accuracy should be formatted");
        assertTrue(markdown.contains("acc=0.8765"), "test accuracy should be formatted");
        assertTrue(markdown.contains("f1=0.8899"));
        assertTrue(markdown.contains("f1=0.8599"));
    }
}
