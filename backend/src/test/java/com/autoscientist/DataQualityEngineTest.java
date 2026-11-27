package com.autoscientist;

import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.analysis.quality.DataQualityEngine;
import com.autoscientist.entity.DataQualityReport;
import com.autoscientist.entity.Dataset;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DataQualityEngineTest {

    private final DatasetProfiler profiler = new DatasetProfiler();
    private final DataQualityEngine qualityEngine = new DataQualityEngine();

    @Test
    void testQualityAuditWithDuplicatesAndMissingness() throws Exception {
        String csv = "id,category,value\n" +
                "1,A,10.0\n" +
                "2,A,20.0\n" +
                "3,B,NA\n" +
                "1,A,10.0\n"; // duplicate row

        Dataset dataset = Dataset.builder().name("Audit Test").build();
        ByteArrayInputStream in1 = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DatasetProfiler.ProfilingResult profResult = profiler.profile(in1, dataset);

        ByteArrayInputStream in2 = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DataQualityReport report = qualityEngine.evaluate(in2, dataset, profResult.getColumns());

        assertNotNull(report);
        assertTrue(report.getOverallScore() < 100.0, "Score should reflect duplicate row and missing value");
        assertTrue(report.getTotalIssues() > 0);

        boolean duplicateFound = report.getIssues().stream()
                .anyMatch(issue -> issue.getIssueType().equals("DUPLICATE_ROWS"));
        assertTrue(duplicateFound, "Should report duplicate row");
    }

    @Test
    void flagsInvalidNumericValues() throws Exception {
        String csv = "measurement\n10\n22\n8\n15\n31\n7\n19\n24\n12\nnot-a-number\n";
        DataQualityReport report = evaluate(csv);

        assertTrue(report.getIssues().stream()
                .anyMatch(i -> i.getIssueType().equals("INVALID_DATA_TYPE")),
                "Malformed numeric values must be reported");
    }

    @Test
    void flagsInconsistentNumericSeparators() throws Exception {
        String csv = "measurement\n1.5\n2.5\n3.5\n4.5\n5.5\n6.5\n7.5\n8.5\n9.5\n\"10,5\"\n";
        DataQualityReport report = evaluate(csv);

        assertTrue(report.getIssues().stream()
                .anyMatch(i -> i.getIssueType().equals("INCONSISTENT_NUMERIC_FORMAT")),
                "Mixed decimal separators must be reported");
    }

    @Test
    void flagsInconsistentDateFormat() throws Exception {
        String csv = "event_date\n2020-01-01\n2020-01-02\n2020-01-03\n2020-01-04\n"
                + "2020-01-05\n2020-01-06\n2020-01-07\n2020-01-08\n2020-01-09\n13/45/2020\n";
        DataQualityReport report = evaluate(csv);

        assertTrue(report.getIssues().stream()
                .anyMatch(i -> i.getIssueType().equals("INCONSISTENT_DATE_FORMAT")),
                "Unrecognised date values must be reported");
    }

    @Test
    void flagsDuplicateColumns() throws Exception {
        String csv = "a,b\n1,1\n2,2\n3,3\n4,4\n5,5\n";
        DataQualityReport report = evaluate(csv);

        assertTrue(report.getIssues().stream()
                .anyMatch(i -> i.getIssueType().equals("DUPLICATE_COLUMN")),
                "Identical columns must be reported as redundant");
    }

    private DataQualityReport evaluate(String csv) throws Exception {
        Dataset dataset = Dataset.builder().name("Audit Test").build();
        DatasetProfiler.ProfilingResult profResult =
                profiler.profile(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), dataset);
        return qualityEngine.evaluate(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                dataset, profResult.getColumns());
    }
}
