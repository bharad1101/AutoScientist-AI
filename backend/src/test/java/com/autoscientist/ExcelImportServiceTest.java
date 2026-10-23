package com.autoscientist;

import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.util.ExcelImportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelImportServiceTest {

    private final ExcelImportService excel = new ExcelImportService();
    private final DatasetProfiler profiler = new DatasetProfiler();

    private byte[] workbook() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Study Data");
            String[][] data = {
                    {"PatientID", "Age", "Gender", "Outcome"},
                    {"P001", "45", "F", "1"},
                    {"P002", "52", "M", "0"},
                    {"P003", "38", "F", "1"},
                    {"P004", "61", "M", "0"}
            };
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            wb.createSheet("Notes");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "study.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    @Test
    void listsSheets() throws Exception {
        List<ExcelImportService.SheetInfo> sheets = excel.listSheets(file(workbook()));
        assertEquals(2, sheets.size());
        assertEquals("Study Data", sheets.get(0).getName());
        assertEquals(5, sheets.get(0).getRowCount());
        assertEquals(4, sheets.get(0).getColumnCount());
        assertEquals("Notes", sheets.get(1).getName());
    }

    @Test
    void convertsSheetToRectangularCsvAndProfiles() throws Exception {
        MockMultipartFile f = file(workbook());
        int headerRow = excel.detectHeaderRow(f, "Study Data", 0);
        assertEquals(1, headerRow);

        ExcelImportService.ConversionResult conv = excel.toCsv(f, "Study Data", 0, headerRow);
        assertEquals("Study Data", conv.getSheetName());
        assertEquals(4, conv.getDataRows());
        assertEquals(4, conv.getColumns());

        String csv = conv.getCsv();
        String[] lines = csv.replace("\r", "").split("\n");
        assertEquals(5, lines.length); // header + 4 rows
        assertEquals("PatientID,Age,Gender,Outcome", lines[0]);

        // End-to-end: the rendition must profile like a normal CSV upload.
        Dataset dataset = Dataset.builder().name("excel-test").build();
        DatasetProfiler.ProfilingResult prof = profiler.profile(
                new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), dataset);

        assertEquals(4, prof.getRowCount());
        assertEquals(4, prof.getColumnCount());

        DatasetColumn age = prof.getColumns().stream()
                .filter(c -> c.getColumnName().equals("Age")).findFirst().orElseThrow();
        assertEquals("NUMERIC", age.getDataType());
        assertEquals(38.0, age.getMinVal());
        assertEquals(61.0, age.getMaxVal());

        DatasetColumn pid = prof.getColumns().stream()
                .filter(c -> c.getColumnName().equals("PatientID")).findFirst().orElseThrow();
        assertEquals("IDENTIFIER", pid.getDataType());
    }

    @Test
    void picksNamedSheetAndRejectsMissingSheet() throws Exception {
        MockMultipartFile f = file(workbook());
        // Empty sheet must fail with a clear message rather than an index error.
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> excel.toCsv(f, "Notes", 0, 1));
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> excel.toCsv(f, "Nope", 0, 1));
    }

    @Test
    void rejectsHeaderRowOutsideSheet() throws Exception {
        MockMultipartFile f = file(workbook());
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> excel.toCsv(f, "Study Data", 0, 0));
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> excel.toCsv(f, "Study Data", 0, 99));
    }

    @Test
    void padsRaggedRowsAndSkipsBlankRows() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Ragged");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("A");
            h.createCell(1).setCellValue("B");
            sheet.createRow(1); // fully blank → skipped
            Row shortRow = sheet.createRow(2);
            shortRow.createCell(0).setCellValue("only-first");
            Row longRow = sheet.createRow(3);
            longRow.createCell(0).setCellValue("x");
            longRow.createCell(1).setCellValue("y");
            longRow.createCell(2).setCellValue("extra");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            MockMultipartFile f = new MockMultipartFile("file", "ragged.xlsx",
                    "application/octet-stream", out.toByteArray());
            ExcelImportService.ConversionResult conv = excel.toCsv(f, null, 0, 1);
            String[] lines = conv.getCsv().replace("\r", "").split("\n");
            assertEquals(3, lines.length); // header + 2 data rows (blank skipped)
            assertTrue(lines[1].startsWith("only-first"));
            assertTrue(lines[1].endsWith(",") || lines[1].endsWith(",,"));
        }
    }

    @Test
    void legacyXlsFilenameAcceptedAndUnsupportedRejected() {
        assertTrue(ExcelImportService.isSupportedFilename("data.csv"));
        assertTrue(ExcelImportService.isSupportedFilename("data.xls"));
        assertTrue(ExcelImportService.isSupportedFilename("DATA.XLSX"));
        assertFalse(ExcelImportService.isSupportedFilename("data.pdf"));
        assertFalse(ExcelImportService.isSupportedFilename(null));
    }
}
