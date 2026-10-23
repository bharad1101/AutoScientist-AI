package com.autoscientist.analysis.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports the result tables contained in a generated report. Tables are parsed
 * back out of the report Markdown (which this application itself writes), so the
 * export always matches the report the user is looking at.
 */
@Service
public class ReportExportService {

    public static class TableSection {
        public String title;
        public List<String> headers = new ArrayList<>();
        public List<List<String>> rows = new ArrayList<>();
    }

    public String toCsv(String markdown) {
        StringBuilder sb = new StringBuilder();
        for (TableSection table : parseTables(markdown)) {
            sb.append("# ").append(table.title).append("\n");
            sb.append(joinCsv(table.headers)).append("\n");
            for (List<String> row : table.rows) {
                sb.append(joinCsv(row)).append("\n");
            }
            sb.append("\n");
        }
        if (sb.length() == 0) {
            sb.append("# No result tables were found in this report.\n");
        }
        return sb.toString();
    }

    public byte[] toExcel(String title, String markdown) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Sheet meta = workbook.createSheet("Report");
            Row metaHeader = meta.createRow(0);
            Cell metaTitle = metaHeader.createCell(0);
            metaTitle.setCellValue(title);
            metaTitle.setCellStyle(headerStyle);
            meta.createRow(1).createCell(0).setCellValue("Exported from AutoScientist AI — tables below mirror the report.");

            List<TableSection> tables = parseTables(markdown);
            java.util.Set<String> usedNames = new java.util.HashSet<>();
            usedNames.add("Report");
            int index = 1;
            for (TableSection table : tables) {
                String name = sheetName(table.title, index++, usedNames);
                Sheet sheet = workbook.createSheet(name);
                Row header = sheet.createRow(0);
                for (int c = 0; c < table.headers.size(); c++) {
                    Cell cell = header.createCell(c);
                    cell.setCellValue(table.headers.get(c));
                    cell.setCellStyle(headerStyle);
                }
                for (int r = 0; r < table.rows.size(); r++) {
                    Row row = sheet.createRow(r + 1);
                    List<String> values = table.rows.get(r);
                    for (int c = 0; c < values.size(); c++) {
                        row.createCell(c).setCellValue(values.get(c));
                    }
                }
                for (int c = 0; c < table.headers.size(); c++) {
                    sheet.autoSizeColumn(c);
                    int width = sheet.getColumnWidth(c);
                    sheet.setColumnWidth(c, Math.min(60 * 256, Math.max(width + 512, 12 * 256)));
                }
            }

            if (tables.isEmpty()) {
                Sheet empty = workbook.createSheet("No tables");
                empty.createRow(0).createCell(0).setCellValue("This report contained no result tables.");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel export failed: " + e.getMessage(), e);
        }
    }

    public List<TableSection> parseTables(String markdown) {
        List<TableSection> tables = new ArrayList<>();
        if (markdown == null) return tables;
        String heading = "Report";
        TableSection current = null;
        int tableCounter = 0;

        for (String raw : markdown.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("## ")) {
                heading = clean(line.substring(3));
                current = null;
                continue;
            }
            if (line.startsWith("### ")) {
                heading = clean(line.substring(4));
                current = null;
                continue;
            }
            if (line.startsWith("|") && line.endsWith("|") && line.length() > 2) {
                List<String> cells = splitRow(line);
                boolean separator = !cells.isEmpty() && cells.stream()
                        .allMatch(c -> c.replace(":", "").replace("-", "").isBlank());
                if (separator) continue;
                if (current == null) {
                    current = new TableSection();
                    current.title = heading + " (table " + (++tableCounter) + ")";
                    current.headers = cells;
                    tables.add(current);
                } else {
                    current.rows.add(cells);
                }
            } else {
                current = null;
            }
        }
        return tables;
    }

    private List<String> splitRow(String line) {
        String inner = line.substring(1, line.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String cell : inner.split("\\|", -1)) {
            cells.add(clean(cell));
        }
        return cells;
    }

    private String clean(String s) {
        return s.replace("`", "").replace("**", "").replace("★", "").trim();
    }

    private String joinCsv(List<String> cells) {
        List<String> escaped = new ArrayList<>();
        for (String c : cells) {
            String v = c == null ? "" : c;
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                v = "\"" + v.replace("\"", "\"\"") + "\"";
            }
            escaped.add(v);
        }
        return String.join(",", escaped);
    }

    private String sheetName(String title, int index, java.util.Set<String> used) {
        String base = title.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (base.isEmpty()) base = "Table " + index;
        if (base.length() > 28) base = base.substring(0, 28);
        String name = base;
        int suffix = 1;
        while (used.contains(name)) {
            String tail = " (" + suffix++ + ")";
            name = base.substring(0, Math.min(base.length(), 31 - tail.length())) + tail;
        }
        used.add(name);
        return name;
    }
}
