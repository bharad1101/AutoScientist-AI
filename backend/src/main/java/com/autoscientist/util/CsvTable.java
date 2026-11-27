package com.autoscientist.util;

import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.ResourceLimitExceededException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A single, validated parse of a CSV upload held in column-major form.
 *
 * <p>Upload previously parsed the same bytes three times (validation, profiling,
 * quality). Keeping one parse lets the profiler and quality audit share the data
 * without changing any analytical results.</p>
 */
public final class CsvTable {

    private final List<String> headers;
    private final List<List<String>> columns;
    private final int rowCount;
    private final int duplicateRowCount;

    private CsvTable(List<String> headers, List<List<String>> columns,
                     int rowCount, int duplicateRowCount) {
        this.headers = headers;
        this.columns = columns;
        this.rowCount = rowCount;
        this.duplicateRowCount = duplicateRowCount;
    }

    public static CsvTable read(InputStream inputStream) {
        return read(inputStream, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Parse a CSV while enforcing explicit row/column budgets. The parser streams
     * from the reader; exceeding a budget aborts immediately rather than growing
     * the in-memory column store without bound. No rows are silently dropped.
     */
    public static CsvTable read(InputStream inputStream, int maxRows, int maxColumns) {
        return read(inputStream, maxRows, maxColumns, (long) maxRows * maxColumns);
    }

    /**
     * Parse with row, column and total-cell budgets. The cell budget catches wide
     * files whose row and column counts are individually acceptable but whose
     * combined size would dominate memory.
     */
    public static CsvTable read(InputStream inputStream, int maxRows, int maxColumns, long maxCells) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(false)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            if (headers.isEmpty()) {
                throw new BadRequestException("CSV validation failed: Missing header row or empty file.");
            }
            if (headers.size() > maxColumns) {
                throw new ResourceLimitExceededException(String.format(
                        "CSV has %d columns, which exceeds the configured column limit of %d.",
                        headers.size(), maxColumns));
            }

            int columnCount = headers.size();
            List<List<String>> columns = new ArrayList<>(columnCount);
            for (int i = 0; i < columnCount; i++) {
                columns.add(new ArrayList<>());
            }

            Set<String> uniqueRows = new HashSet<>();
            int rowCount = 0;
            int duplicateRowCount = 0;

            for (CSVRecord record : parser) {
                if (rowCount >= maxRows) {
                    throw new ResourceLimitExceededException(String.format(
                            "CSV exceeds the configured row limit of %d rows. "
                                    + "Reduce the file size or raise the row budget.",
                            maxRows));
                }
                if (record.size() != columnCount) {
                    throw new BadRequestException(String.format(
                            "CSV validation failed: Inconsistent row length at row %d "
                                    + "(expected %d columns, but found %d).",
                            rowCount + 1, columnCount, record.size()));
                }
                if ((long) (rowCount + 1) * columnCount > maxCells) {
                    throw new ResourceLimitExceededException(String.format(
                            "CSV exceeds the configured total cell limit of %d cells.", maxCells));
                }

                StringBuilder rowKey = new StringBuilder();
                for (int i = 0; i < columnCount; i++) {
                    String value = record.get(i);
                    columns.get(i).add(value);
                    if (i > 0) {
                        rowKey.append("|||");
                    }
                    rowKey.append(value);
                }
                if (!uniqueRows.add(rowKey.toString())) {
                    duplicateRowCount++;
                }
                rowCount++;
            }

            if (rowCount == 0) {
                throw new BadRequestException("CSV validation failed: File contains headers but no data rows.");
            }

            return new CsvTable(headers, columns, rowCount, duplicateRowCount);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Malformed CSV structure: " + e.getMessage());
        }
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<String> getColumn(int index) {
        return columns.get(index);
    }

    public List<List<String>> getColumns() {
        return columns;
    }

    public int getColumnCount() {
        return headers.size();
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getDuplicateRowCount() {
        return duplicateRowCount;
    }
}
