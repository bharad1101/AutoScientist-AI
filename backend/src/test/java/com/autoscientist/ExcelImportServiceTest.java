package com.autoscientist;

import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.config.ResourceBudgetProperties;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.util.ExcelImportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    void conversionRejectsWorksheetExceedingConfiguredColumnBudget() throws Exception {
        ExcelImportService limited = limitedService(2, 100, 1000, 5, 1);
        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> limited.toCsv(file(workbook()), "Study Data", 0, 1));
    }

    @Test
    void conversionRejectsWorksheetExceedingConfiguredRowAndCellBudgets() throws Exception {
        ExcelImportService rowLimited = limitedService(10, 2, 1000, 5, 1);
        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> rowLimited.toCsv(file(workbook()), "Study Data", 0, 1));

        ExcelImportService cellLimited = limitedService(10, 100, 5, 5, 1);
        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> cellLimited.toCsv(file(workbook()), "Study Data", 0, 1));
    }

    @Test
    void conversionRejectsWorkbookExceedingSheetBudgetAndMalformedBytes() throws Exception {
        ExcelImportService sheetLimited = limitedService(10, 100, 1000, 1, 1);
        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> sheetLimited.listSheets(file(workbook())));

        MockMultipartFile corrupt = new MockMultipartFile("file", "corrupt.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-a-workbook".getBytes(StandardCharsets.UTF_8));
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> excel.toCsv(corrupt, null, 0, 1));
    }

    private ExcelImportService limitedService(int maxColumns, int maxRows, int maxCells,
                                              int maxSheets, int maxMb) {
        ResourceBudgetProperties budget = new ResourceBudgetProperties();
        budget.setMaxColumns(maxColumns);
        budget.setMaxRows(maxRows);
        budget.setMaxCells(maxCells);
        budget.setMaxSheets(maxSheets);
        budget.setMaxMb(maxMb);
        return new ExcelImportService(budget);
    }

    @Test
    void listSheetsRejectsWorksheetExceedingConfiguredRowBudget() throws Exception {
        ExcelImportService limited = limitedService(10, 2, 1000, 5, 1);

        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> limited.listSheets(file(workbook())));
    }

    @Test
    void conversionRejectsOversizedSingleCell() throws Exception {
        String hugeValue = "x".repeat(ExcelImportService.MAX_CELL_CHARS + 1);
        byte[] bytes = singleCellWorkbook(hugeValue);

        ExcelImportService service = limitedService(10, 100, 10_000, 5, 50);
        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> service.toCsv(file(bytes), "Data", 0, 1));
    }

    @Test
    void conversionRejectsWorksheetExceedingConfiguredOutputByteBudget() throws Exception {
        String cellValue = "x".repeat(20_000);
        byte[] bytes = wideWorkbook(12, 6, cellValue);

        ExcelImportService service = limitedService(20, 100, 100_000, 5, 1);
        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> service.toCsv(file(bytes), "Data", 0, 1));
    }

    @Test
    void validZipWithCorruptOoxmlIsRejectedAsBadRequest() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zip.write("not-valid-xml".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> excel.toCsv(file(out.toByteArray()), null, 0, 1));
    }

    @Test
    void decompressionBombIsRejectedAsResourceLimit() throws Exception {
        java.util.Random random = new java.util.Random(42);
        StringBuilder compressible = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            byte[] block = new byte[16_000];
            random.nextBytes(block);
            compressible.append(java.util.Base64.getEncoder().encodeToString(block).repeat(70));
        }
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"pad\" ContentType=\"" + compressible + "\"/>"
                + "</Types>";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
            zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zip.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> excel.toCsv(file(out.toByteArray()), null, 0, 1));
    }

    @Test
    void decompressionLimitFailuresAreRecognisedThroughCauseChain() {
        assertTrue(ExcelImportService.isDecompressionLimitFailure(
                new IOException("Zip bomb detected! The file would exceed the max size")));
        assertTrue(ExcelImportService.isDecompressionLimitFailure(
                new IllegalStateException("wrapper", new IOException("max. inflate ratio exceeded"))));
        assertFalse(ExcelImportService.isDecompressionLimitFailure(new IOException("socket closed")));
    }

    @Test
    void previewRejectsOversizedUploadBeforeParsing() {
        ResourceBudgetProperties budget = new ResourceBudgetProperties();
        budget.setMaxMb(1);
        com.autoscientist.service.ImportPreviewService preview =
                new com.autoscientist.service.ImportPreviewService(excel, profiler, budget);

        MockMultipartFile oversized = new MockMultipartFile("file", "large.csv",
                "text/csv", new byte[2 * 1024 * 1024]);

        assertThrows(com.autoscientist.exception.ResourceLimitExceededException.class,
                () -> preview.preview(oversized, null));
    }

    private byte[] singleCellWorkbook(String value) throws Exception {
        byte[] base;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Name");
            sheet.createRow(1).createCell(0).setCellValue("PLACEHOLDER");
            base = write(wb);
        }

        ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream in =
                     new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(base));
             java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(rewritten)) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] content = in.readAllBytes();
                if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    String xml = new String(content, StandardCharsets.UTF_8)
                            .replace("PLACEHOLDER", value);
                    content = xml.getBytes(StandardCharsets.UTF_8);
                }
                out.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                out.write(content);
                out.closeEntry();
            }
        }
        return rewritten.toByteArray();
    }

    private byte[] wideWorkbook(int rows, int columns, String value) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            for (int r = 0; r < rows; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < columns; c++) {
                    row.createCell(c).setCellValue(r == 0 ? "col_" + c : value);
                }
            }
            return write(wb);
        }
    }

    private byte[] write(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
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
