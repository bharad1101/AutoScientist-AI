package com.autoscientist.analysis.association;

import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.entity.Association;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class AssociationEngine {

    private final StatisticalTestEngine testEngine;
    private final com.autoscientist.config.ResourceBudgetProperties budgetProperties;

    public AssociationEngine(StatisticalTestEngine testEngine) {
        this(testEngine, new com.autoscientist.config.ResourceBudgetProperties());
    }

    @Autowired
    public AssociationEngine(StatisticalTestEngine testEngine, com.autoscientist.config.ResourceBudgetProperties budgetProperties) {
        this.testEngine = testEngine;
        this.budgetProperties = budgetProperties;
    }



    public List<Association> discoverAssociations(InputStream inputStream, Dataset dataset, List<DatasetColumn> columns) throws Exception {
        if (columns == null || columns.size() < 2) {
            return new ArrayList<>();
        }

        List<String> headers;
        Map<String, DatasetColumn> colMeta = new HashMap<>();
        for (DatasetColumn c : columns) {
            if (c != null && c.getColumnName() != null) {
                colMeta.put(c.getColumnName(), c);
            }
        }

        Map<String, ColumnData> colData = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(false)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            headers = new ArrayList<>(parser.getHeaderNames());
            int width = headers.size();
            boolean[] numeric = new boolean[width];
            DoubleColumn[] numericColumns = new DoubleColumn[width];
            TextColumn[] textColumns = new TextColumn[width];

            for (int i = 0; i < width; i++) {
                DatasetColumn meta = colMeta.get(headers.get(i));
                numeric[i] = meta != null && "NUMERIC".equals(meta.getDataType());
                if (numeric[i]) {
                    numericColumns[i] = new DoubleColumn();
                } else {
                    textColumns[i] = new TextColumn();
                }
            }

            // Enforce configurable column budget to avoid OOM or excessive compute time
    if (width > budgetProperties.getMaxColumns()) {
      throw new IllegalArgumentException("Dataset has " + width + " columns, exceeding the configured maxColumns of " + budgetProperties.getMaxColumns() + " for association analysis.");
    }
    for (CSVRecord record : parser) {
                for (int i = 0; i < width; i++) {
                    String value = i < record.size() ? record.get(i) : "";
                    if (numeric[i]) {
                        numericColumns[i].add(value);
                    } else {
                        textColumns[i].add(value);
                    }
                }
            }

            for (int i = 0; i < width; i++) {
                String name = headers.get(i);
                if (numeric[i]) {
                    colData.put(name, new ColumnData(numericColumns[i].toArray(), null));
                } else {
                    colData.put(name, new ColumnData(null, textColumns[i].toArray()));
                }
            }
        }

        List<Association> associations = new ArrayList<>();
        int numCols = columns.size();

        for (int i = 0; i < numCols; i++) {
            DatasetColumn colA = columns.get(i);
            String nameA = colA != null ? colA.getColumnName() : null;
            String typeA = colA != null ? colA.getDataType() : null;
            ColumnData dataA = nameA != null ? colData.get(nameA) : null;

            for (int j = i + 1; j < numCols; j++) {
                DatasetColumn colB = columns.get(j);
                String nameB = colB != null ? colB.getColumnName() : null;
                String typeB = colB != null ? colB.getDataType() : null;
                ColumnData dataB = nameB != null ? colData.get(nameB) : null;

                if (typeA == null || typeB == null || dataA == null || dataB == null) continue;

                // Skip non-informative ID columns (IDENTIFIER, 100% unique strings, or single constant values)
                if ("IDENTIFIER".equals(typeA) || "IDENTIFIER".equals(typeB)) continue;
                int uA = colA.getUniqueCount() != null ? colA.getUniqueCount() : 0;
                int uB = colB.getUniqueCount() != null ? colB.getUniqueCount() : 0;
                if (uA <= 1 || uB <= 1) continue;
                if (("TEXT".equals(typeA) && uA > 50) || ("TEXT".equals(typeB) && uB > 50)) continue;
                if (!dataA.hasValuesFor(typeA) || !dataB.hasValuesFor(typeB)) continue;

                StatisticalTestEngine.TestResult result = null;

                try {
                    if ("NUMERIC".equals(typeA) && "NUMERIC".equals(typeB)) {
                        result = testEngine.computePearsonCorrelation(nameA, nameB, dataA.numbers, dataB.numbers);
                    } else if ("NUMERIC".equals(typeA) && ("CATEGORICAL".equals(typeB) || "BOOLEAN".equals(typeB))) {
                        if (uB == 2) {
                            result = testEngine.computeWelchTTest(nameA, nameB, dataA.numbers, dataB.texts);
                        } else if (uB > 2 && uB <= 20) {
                            result = testEngine.computeOneWayAnova(nameA, nameB, dataA.numbers, dataB.texts);
                        }
                    } else if (("CATEGORICAL".equals(typeA) || "BOOLEAN".equals(typeA)) && "NUMERIC".equals(typeB)) {
                        if (uA == 2) {
                            result = testEngine.computeWelchTTest(nameB, nameA, dataB.numbers, dataA.texts);
                        } else if (uA > 2 && uA <= 20) {
                            result = testEngine.computeOneWayAnova(nameB, nameA, dataB.numbers, dataA.texts);
                        }
                    } else if (("CATEGORICAL".equals(typeA) || "BOOLEAN".equals(typeA)) &&
                            ("CATEGORICAL".equals(typeB) || "BOOLEAN".equals(typeB))) {
                        if (uA <= 25 && uB <= 25) {
                            result = testEngine.computeChiSquare(nameA, nameB, dataA.texts, dataB.texts);
                        }
                    }
                } catch (Exception ex) {
                    // Pathological pair (e.g. math singularity or unexpected string) should not abort remaining pairs
                    continue;
                }

                if (result != null && result.getSampleSize() > 0) {
                    Association assoc = Association.builder()
                            .dataset(dataset)
                            .var1(nameA)
                            .var2(nameB)
                            .var1Type(typeA)
                            .var2Type(typeB)
                            .testType(result.getTestType())
                            .statistic(result.getStatistic())
                            .pValue(result.getPValue())
                            .effectSize(result.getEffectSize())
                            .sampleSize(result.getSampleSize())
                            .degreesOfFreedom(result.getDegreesOfFreedom())
                            .isSignificant(result.isSignificant())
                            .interpretation(result.getInterpretation())
                            .rankScore(result.getRankScore())
                            .ciLower(result.getCiLower())
                            .ciUpper(result.getCiUpper())
                            .ciLevel(result.getCiLevel())
                            .ciMethod(result.getCiMethod())
                            .ciTarget(result.getCiTarget())
                            .ciUnavailableReason(result.getCiUnavailableReason())
                            .effectSizeCiLower(result.getEffectSizeCiLower())
                            .effectSizeCiUpper(result.getEffectSizeCiUpper())
                            .build();

                    associations.add(assoc);
                }
            }
        }

        // Rank associations by rankScore descending
        associations.sort((a, b) -> Double.compare(b.getRankScore(), a.getRankScore()));

        return associations;
    }

    private static final class ColumnData {
        private final double[] numbers;
        private final String[] texts;

        private ColumnData(double[] numbers, String[] texts) {
            this.numbers = numbers;
            this.texts = texts;
        }

        private boolean hasValuesFor(String type) {
            if ("NUMERIC".equals(type)) {
                return numbers != null;
            }
            return texts != null;
        }
    }

    private static final class DoubleColumn {
        private double[] values = new double[1024];
        private int size = 0;

        private void add(String raw) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = parseNumber(raw);
        }

        private double[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static final class TextColumn {
        private String[] values = new String[1024];
        private int size = 0;

        private void add(String raw) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = raw;
        }

        private String[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static double parseNumber(String raw) {
        if (raw == null) return Double.NaN;
        String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("na") || value.equalsIgnoreCase("nan")
                || value.equalsIgnoreCase("null")) {
            return Double.NaN;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : Double.NaN;
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }
}
