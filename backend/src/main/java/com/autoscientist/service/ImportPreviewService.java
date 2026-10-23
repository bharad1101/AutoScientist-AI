package com.autoscientist.service;

import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.dto.dataset.DatasetColumnDto;
import com.autoscientist.dto.importing.ImportOptionsDto;
import com.autoscientist.dto.importing.ImportPreviewDto;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.util.CsvValidator;
import com.autoscientist.util.ExcelImportService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stateless import-wizard backend: given an uploaded file (and any options the
 * user has changed), report what will be imported — sheets, header row, preview
 * rows, inferred types and warnings — without persisting anything.
 *
 * The final commit happens in {@link DatasetService#uploadDataset}, which
 * applies the same options so preview and import can never disagree.
 */
@Service
@RequiredArgsConstructor
public class ImportPreviewService {

    private static final int PREVIEW_ROW_LIMIT = 25;
    /** Identifier-name heuristics mirrored from the backend profiler for early warnings. */
    private static final Set<String> ID_NAME_TOKENS = Set.of(
            "id", "uuid", "guid", "index", "rowid", "recordid", "key");

    private final ExcelImportService excelImportService;
    private final DatasetProfiler datasetProfiler;

    public ImportPreviewDto preview(MultipartFile file, ImportOptionsDto options) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file selected. Choose a .csv, .xls or .xlsx file.");
        }
        String filename = file.getOriginalFilename();
        if (!ExcelImportService.isSupportedFilename(filename)) {
            throw new BadRequestException("Unsupported file type. Upload a .csv, .xls or .xlsx file.");
        }
        ImportOptionsDto opts = options != null ? options : ImportOptionsDto.builder().build();
        boolean excel = ExcelImportService.isExcelFilename(filename);

        ImportPreviewDto.ImportPreviewDtoBuilder builder = ImportPreviewDto.builder()
                .originalFilename(filename)
                .fileSize(file.getSize())
                .sourceFormat(sourceFormat(filename))
                .excel(excel);

        String csvContent;
        String sheetName = null;
        Integer headerRow = null;
        boolean headerRowDetected = false;

        if (excel) {
            List<ExcelImportService.SheetInfo> sheets = excelImportService.listSheets(file);
            if (sheets.isEmpty()) {
                throw new BadRequestException("The workbook contains no sheets.");
            }
            ExcelImportService.SheetInfo chosen = chooseSheet(sheets, opts);
            sheetName = chosen.getName();

            if (opts.getHeaderRow() != null) {
                headerRow = opts.getHeaderRow();
            } else {
                headerRow = excelImportService.detectHeaderRow(file, chosen.getName(), chosen.getIndex());
                headerRowDetected = true;
            }

            ExcelImportService.ConversionResult conv =
                    excelImportService.toCsv(file, chosen.getName(), chosen.getIndex(), headerRow);
            csvContent = conv.getCsv();

            builder.sheets(sheets.stream()
                    .map(s -> ImportPreviewDto.SheetDto.builder()
                            .index(s.getIndex())
                            .name(s.getName())
                            .rowCount(s.getRowCount())
                            .columnCount(s.getColumnCount())
                            .build())
                    .toList());
        } else {
            try {
                csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BadRequestException("Could not read the file: " + e.getMessage());
            }
            headerRow = 1;
            headerRowDetected = false;
            builder.sheets(List.of());
        }

        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
        CsvValidator.ValidationResult validation =
                CsvValidator.validateCsv(new ByteArrayInputStream(csvBytes));

        List<String> headers = validation.getHeaders();
        List<List<String>> previewRows = readPreviewRows(csvContent);

        // Type inference on the same profiler the real import uses, over the
        // preview sample — labelled as a sample in the response.
        List<DatasetColumnDto> columnDtos = new ArrayList<>();
        Dataset scratch = Dataset.builder().name("_preview").rowCount(previewRows.size())
                .columnCount(headers.size()).build();
        try {
            DatasetProfiler.ProfilingResult prof = datasetProfiler.profile(
                    new ByteArrayInputStream(csvBytes), scratch);
            for (DatasetColumn col : prof.getColumns()) {
                columnDtos.add(DatasetColumnDto.builder()
                        .columnName(col.getColumnName())
                        .dataType(col.getDataType())
                        .typeConfidence(col.getTypeConfidence())
                        .missingCount(col.getMissingCount())
                        .missingPct(col.getMissingPct())
                        .uniqueCount(col.getUniqueCount())
                        .minVal(col.getMinVal())
                        .maxVal(col.getMaxVal())
                        .meanVal(col.getMeanVal())
                        .medianVal(col.getMedianVal())
                        .stdDev(col.getStdDev())
                        .skewness(col.getSkewness())
                        .build());
            }
        } catch (Exception e) {
            throw new BadRequestException("The data could not be parsed: " + e.getMessage());
        }

        builder.headers(headers)
                .previewRows(previewRows)
                .rowCount(validation.getRowCount())
                .columnCount(validation.getColumnCount())
                .columns(columnDtos)
                .sheetName(sheetName)
                .headerRow(headerRow)
                .headerRowDetected(headerRowDetected)
                .warnings(buildWarnings(headers, validation, columnDtos, excel, headerRowDetected));

        return builder.build();
    }

    // ------------------------------------------------------------- helpers

    private ExcelImportService.SheetInfo chooseSheet(List<ExcelImportService.SheetInfo> sheets,
                                                     ImportOptionsDto opts) {
        if (opts.getSheetName() != null && !opts.getSheetName().isBlank()) {
            return sheets.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(opts.getSheetName().trim()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Sheet '" + opts.getSheetName() + "' does not exist in this workbook."));
        }
        if (opts.getSheetIndex() != null
                && opts.getSheetIndex() >= 0 && opts.getSheetIndex() < sheets.size()) {
            return sheets.get(opts.getSheetIndex());
        }
        return sheets.get(0);
    }

    private List<List<String>> readPreviewRows(String csvContent) {
        List<List<String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(new InputStreamReader(
                        new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8))) {
            int expected = parser.getHeaderNames().size();
            for (CSVRecord record : parser) {
                if (rows.size() >= PREVIEW_ROW_LIMIT) break;
                List<String> row = new ArrayList<>(expected);
                for (int i = 0; i < expected; i++) {
                    row.add(i < record.size() ? record.get(i) : "");
                }
                rows.add(row);
            }
        } catch (IOException e) {
            return rows;
        }
        return rows;
    }

    private List<String> buildWarnings(List<String> headers,
                                       CsvValidator.ValidationResult validation,
                                       List<DatasetColumnDto> columns,
                                       boolean excel, boolean headerRowDetected) {
        List<String> warnings = new ArrayList<>();

        Set<String> lower = new HashSet<>();
        Set<String> dupes = new LinkedHashSet<>();
        for (String h : headers) {
            String k = h.toLowerCase(Locale.ROOT);
            if (!lower.add(k)) dupes.add(h);
        }
        if (!dupes.isEmpty()) {
            warnings.add("Duplicate header name(s) renamed on import: " + String.join(", ", dupes));
        }
        int blank = 0;
        for (String h : headers) {
            if (h == null || h.isBlank() || h.matches("column_\\d+")) blank++;
        }
        if (blank > 0) {
            warnings.add(blank + " column(s) had an empty header — they will be imported as column_1, column_2, ...");
        }
        if (validation.getRowCount() == 0) {
            warnings.add("The file has a header row but no data rows.");
        }
        if (headerRowDetected) {
            warnings.add("The header row was auto-detected — verify it in the preview before importing.");
        }
        if (excel) {
            warnings.add("Excel files are converted to CSV at import time; formulas use the values "
                    + "last saved by Excel. The uploaded workbook itself is never modified.");
        }

        List<String> idLike = new ArrayList<>();
        for (DatasetColumnDto c : columns) {
            if ("IDENTIFIER".equals(c.getDataType())) {
                idLike.add(c.getColumnName());
            } else {
                String n = c.getColumnName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
                String bare = n.replaceAll("_", "");
                boolean tokenHit = ID_NAME_TOKENS.stream().anyMatch(t ->
                        n.equals(t) || n.endsWith("_" + t) || n.startsWith(t + "_") || n.contains("_" + t + "_"));
                if (tokenHit || (bare.endsWith("id") && bare.length() > 2 && !"valid".equals(bare)
                        && !"invalid".equals(bare) && !"paid".equals(bare))) {
                    idLike.add(c.getColumnName());
                }
            }
        }
        if (!idLike.isEmpty()) {
            warnings.add("Column(s) that look like identifiers: " + String.join(", ", idLike)
                    + ". They are excluded from analysis by default — re-include them in the wizard "
                    + "if they are genuine variables.");
        }

        for (DatasetColumnDto c : columns) {
            if (c.getMissingPct() != null && c.getMissingPct() >= 40.0) {
                warnings.add("Column '" + c.getColumnName() + "' is "
                        + String.format(Locale.ROOT, "%.0f", c.getMissingPct())
                        + "% missing — consider excluding it.");
            }
        }

        if (validation.getRowCount() > 0 && validation.getRowCount() < 5) {
            warnings.add("Only " + validation.getRowCount()
                    + " data row(s): statistical tests and models will be unreliable.");
        }

        return warnings;
    }

    private String sourceFormat(String filename) {
        if (filename == null) return "CSV";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) return "XLSX";
        if (lower.endsWith(".xls")) return "XLS";
        return "CSV";
    }
}
