package com.autoscientist;

import com.autoscientist.analysis.report.ReportExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReportExportServiceTest {

    private final ReportExportService exportService = new ReportExportService();

    private static final String MARKDOWN = """
            # Report
            Some narrative with | a pipe that is not a table.

            | Model | Accuracy |
            |---|---|
            | Random Forest | 0.9100 |
            | Logistic Regression | 0.8700 |

            | Check | Status |
            |---|---|
            | Leakage | PASS |
            """;

    @Test
    @DisplayName("CSV export contains every markdown table, not narrative text")
    void testCsvExport() {
        String csv = exportService.toCsv(MARKDOWN);
        assertTrue(csv.contains("Model,Accuracy"));
        assertTrue(csv.contains("Random Forest,0.9100"));
        assertTrue(csv.contains("Check,Status"));
        assertFalse(csv.contains("Some narrative"));
    }

    @Test
    @DisplayName("Excel export produces a valid XLSX zip with one sheet per table")
    void testExcelExport() {
        byte[] xlsx = exportService.toExcel("Scientific Report", MARKDOWN);
        assertTrue(xlsx.length > 500);
        assertEquals('P', (char) xlsx[0]);
        assertEquals('K', (char) xlsx[1]);
    }
}
