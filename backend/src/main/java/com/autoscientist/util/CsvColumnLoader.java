package com.autoscientist.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Streams selected columns out of a stored CSV analysis file. Used by the
 * ask-your-dataset engine so answers are computed from real rows (never
 * fabricated), while keeping memory bounded.
 */
public final class CsvColumnLoader {

    public static final int DEFAULT_MAX_ROWS = 100_000;

    private CsvColumnLoader() {}

    public static Map<String, List<String>> load(InputStream in, Collection<String> columnNames, int maxRows)
            throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String c : columnNames) out.put(c, new ArrayList<>());
        if (columnNames.isEmpty()) return out;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(false)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            Map<String, String> actualToRequested = new HashMap<>();
            for (String header : parser.getHeaderNames()) {
                for (String requested : columnNames) {
                    if (header.equalsIgnoreCase(requested)) {
                        actualToRequested.put(header, requested);
                    }
                }
            }
            int rows = 0;
            for (CSVRecord record : parser) {
                if (rows++ >= maxRows) break;
                for (Map.Entry<String, String> e : actualToRequested.entrySet()) {
                    List<String> target = out.get(e.getValue());
                    target.add(record.isMapped(e.getKey()) ? record.get(e.getKey()) : "");
                }
            }
        }
        return out;
    }
}
