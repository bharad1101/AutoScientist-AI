package com.autoscientist.util;

import com.autoscientist.exception.BadRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads legacy (.xls) and modern (.xlsx) workbooks and converts a chosen sheet +
 * header row into CSV text, so every downstream engine (profiler, quality, EDA,
 * feature engineering) keeps working on one canonical format.
 *
 * Rules:
 *  - the uploaded workbook itself is never modified — conversion is one-way;
 *  - ragged rows are padded (or header widened) so the CSV is rectangular,
 *    because CSV consumers require consistent row length;
 *  - numbers are written without locale grouping so they stay parseable;
 *  - dates are written ISO-8601;
 *  - formulas use their cached calculated values (no re-evaluation surprises).
 */
@Service
public class ExcelImportService {

    public static final int MAX_PREVIEW_ROWS = 25;
    /** Safety cap: a workbook beyond this many rows is refused instead of OOM-ing. */
    public static final int MAX_SHEET_ROWS = 1_000_000;

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public static boolean isExcelFilename(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    public static boolean isSupportedFilename(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv") || isExcelFilename(filename);
    }

    public static class SheetInfo {
        private final int index;
        private final String name;
        private final int rowCount;
        private final int columnCount;

        public SheetInfo(int index, String name, int rowCount, int columnCount) {
            this.index = index;
            this.name = name;
            this.rowCount = rowCount;
            this.columnCount = columnCount;
        }

        public int getIndex() { return index; }
        public String getName() { return name; }
        public int getRowCount() { return rowCount; }
        public int getColumnCount() { return columnCount; }
    }

    public List<SheetInfo> listSheets(MultipartFile file) {
        try (Workbook wb = openWorkbook(file)) {
            List<SheetInfo> out = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                out.add(new SheetInfo(i, sheet.getSheetName(),
                        sheet.getLastRowNum() + 1, maxColumns(sheet)));
            }
            return out;
        } catch (IOException e) {
            throw new BadRequestException("Could not read workbook: " + e.getMessage());
        }
    }

    public static class ConversionResult {
        private final String csv;
        private final String sheetName;
        private final int headerRow;
        private final int dataRows;
        private final int columns;

        public ConversionResult(String csv, String sheetName, int headerRow, int dataRows, int columns) {
            this.csv = csv;
            this.sheetName = sheetName;
            this.headerRow = headerRow;
            this.dataRows = dataRows;
            this.columns = columns;
        }

        public String getCsv() { return csv; }
        public String getSheetName() { return sheetName; }
        public int getHeaderRow() { return headerRow; }
        public int getDataRows() { return dataRows; }
        public int getColumns() { return columns; }
    }

    /**
     * Convert one sheet to rectangular CSV text.
     *
     * @param sheetIndex 0-based sheet index (ignored when sheetName matches)
     * @param headerRow  1-based row number in the sheet that holds column names
     */
    public ConversionResult toCsv(MultipartFile file, String sheetName, int sheetIndex, int headerRow) {
        try (Workbook wb = openWorkbook(file)) {
            Sheet sheet = resolveSheet(wb, sheetName, sheetIndex);
            if (sheet == null) {
                throw new BadRequestException("Sheet not found in workbook: "
                        + (sheetName != null ? sheetName : "#" + sheetIndex));
            }
            int widthEarly = maxColumns(sheet);
            if (widthEarly == 0 || sheet.getLastRowNum() < 0) {
                throw new BadRequestException("The selected sheet '"
                        + sheet.getSheetName() + "' is empty.");
            }
            if (headerRow < 1 || headerRow > sheet.getLastRowNum() + 1) {
                throw new BadRequestException(String.format(
                        "Header row %d is outside the sheet (rows available: 1-%d).",
                        headerRow, sheet.getLastRowNum() + 1));
            }

            int headerIdx = headerRow - 1;
            int width = maxColumns(sheet);
            if (width == 0) {
                throw new BadRequestException("The selected sheet is empty.");
            }

            List<String> headers = readRow(sheet.getRow(headerIdx), width, wb.getCreationHelper());
            // Header cells left blank get generated names so columns stay addressable.
            for (int i = 0; i < width; i++) {
                if (headers.get(i) == null || headers.get(i).isBlank()) {
                    headers.set(i, "column_" + (i + 1));
                }
            }
            // Duplicate header names break downstream look-ups; disambiguate deterministically.
            List<String> uniqueHeaders = dedupe(headers);

            StringWriter writer = new StringWriter();
            int printed = 0;
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                printer.printRecord(uniqueHeaders);

                int lastRowIdx = sheet.getLastRowNum();
                if (lastRowIdx - headerIdx > MAX_SHEET_ROWS) {
                    throw new BadRequestException("Sheet exceeds the maximum of "
                            + MAX_SHEET_ROWS + " rows.");
                }
                for (int r = headerIdx + 1; r <= lastRowIdx; r++) {
                    Row row = sheet.getRow(r);
                    List<String> cells = readRow(row, width, wb.getCreationHelper());
                    if (isBlankRow(cells)) continue; // skip spacer rows entirely
                    printer.printRecord(cells);
                    printed++;
                }
                if (printed == 0) {
                    throw new BadRequestException(
                            "The selected sheet has a header row but no data rows below it.");
                }
            }
            return new ConversionResult(writer.toString(), sheet.getSheetName(), headerRow,
                    printed, uniqueHeaders.size());
        } catch (IOException e) {
            throw new BadRequestException("Could not convert sheet to CSV: " + e.getMessage());
        }
    }

    /**
     * Best-effort header row detection for a sheet (1-based). Scores the first
     * 10 rows: a row qualifies when its cells are non-empty, distinct and contain
     * at least one letter (a header is textual); the earliest qualifying row wins.
     * Falls back to row 1 so a numeric-looking header still imports.
     */
    public int detectHeaderRow(MultipartFile file, String sheetName, int sheetIndex) {
        try (Workbook wb = openWorkbook(file)) {
            Sheet sheet = resolveSheet(wb, sheetName, sheetIndex);
            if (sheet == null) return 1;
            DataFormatter formatter = new DataFormatter(Locale.US);
            int limit = Math.min(sheet.getLastRowNum(), 9);
            for (int r = 0; r <= limit; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<String> vals = new ArrayList<>();
                for (Cell cell : row) {
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        vals.add(formatCell(cell, formatter, wb.getCreationHelper()).trim());
                    }
                }
                if (vals.isEmpty()) continue;
                java.util.Set<String> distinct = new java.util.HashSet<>(vals);
                boolean hasAlpha = vals.stream().anyMatch(v -> v.chars().anyMatch(Character::isLetter));
                if (distinct.size() == vals.size() && hasAlpha) return r + 1;
            }
            return 1;
        } catch (IOException e) {
            return 1;
        }
    }

    public byte[] toCsvBytes(String csv) {
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    public String csvFileName(String originalFilename, String sheetName) {
        String base = originalFilename == null ? "workbook" : originalFilename;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String sheet = sheetName == null || sheetName.isBlank() ? "sheet" : sheetName;
        return base + "-" + sanitize(sheet) + ".csv";
    }

    // ------------------------------------------------------------------ internal

    private Workbook openWorkbook(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty.");
        }
        if (!isSupportedFilename(file.getOriginalFilename())) {
            throw new BadRequestException(
                    "Unsupported file type. Upload a .csv, .xls or .xlsx file.");
        }
        try {
            // WorkbookFactory auto-detects OLE2 (.xls) vs OOXML (.xlsx).
            Workbook wb = WorkbookFactory.create(file.getInputStream());
            if (wb.getNumberOfSheets() == 0) {
                wb.close();
                throw new BadRequestException("The workbook contains no sheets.");
            }
            return wb;
        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadRequestException("Unreadable or corrupt workbook: " + e.getMessage());
        }
    }

    private Sheet resolveSheet(Workbook wb, String sheetName, int sheetIndex) {
        if (sheetName != null && !sheetName.isBlank()) {
            return wb.getSheet(sheetName);
        }
        if (sheetIndex < 0 || sheetIndex >= wb.getNumberOfSheets()) {
            return null;
        }
        return wb.getSheetAt(sheetIndex);
    }

    private int maxColumns(Sheet sheet) {
        int max = 0;
        for (Row row : sheet) {
            if (row == null) continue;
            short last = row.getLastCellNum();
            if (last > max) max = last;
        }
        return max;
    }

    private List<String> readRow(Row row, int width, CreationHelper helper) {
        DataFormatter formatter = new DataFormatter(Locale.US);
        List<String> cells = new ArrayList<>(width);
        for (int c = 0; c < width; c++) {
            cells.add(row == null ? "" : formatCell(row.getCell(c), formatter, helper));
        }
        return cells;
    }

    private String formatCell(Cell cell, DataFormatter formatter, CreationHelper helper) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            // Use the value Excel last calculated — never invent a different result.
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return formatDate(cell.getDateCellValue());
                }
                // Avoid grouping separators/scientific notation from DataFormatter.
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
                    return String.valueOf((long) v);
                }
                return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
            case BLANK:
            case ERROR:
            default:
                return "";
        }
    }

    private String formatDate(java.util.Date date) {
        LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        if (ldt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            return DATE_FMT.format(ldt.toLocalDate());
        }
        return DATE_TIME_FMT.format(ldt);
    }

    private boolean isBlankRow(List<String> cells) {
        for (String c : cells) {
            if (c != null && !c.isBlank()) return false;
        }
        return true;
    }

    private List<String> dedupe(List<String> headers) {
        List<String> out = new ArrayList<>(headers.size());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i).trim();
            if (h.isEmpty()) h = "column_" + (i + 1);
            String candidate = h;
            int suffix = 2;
            while (!seen.add(candidate.toLowerCase(Locale.ROOT))) {
                candidate = h + "_" + suffix++;
            }
            out.add(candidate);
        }
        return out;
    }

    private String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
