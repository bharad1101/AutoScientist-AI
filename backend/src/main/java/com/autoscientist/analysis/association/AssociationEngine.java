package com.autoscientist.analysis.association;

import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.entity.Association;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class AssociationEngine {

    private final StatisticalTestEngine testEngine;

    public AssociationEngine(StatisticalTestEngine testEngine) {
        this.testEngine = testEngine;
    }

    public List<Association> discoverAssociations(InputStream inputStream, Dataset dataset, List<DatasetColumn> columns) throws Exception {
        List<String> headers;
        List<List<String>> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(false)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            headers = new ArrayList<>(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                List<String> r = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    r.add(record.get(i));
                }
                rows.add(r);
            }
        }

        // Map column name to raw value column list
        Map<String, List<String>> colData = new HashMap<>();
        Map<String, DatasetColumn> colMeta = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String colName = headers.get(i);
            List<String> colVals = new ArrayList<>();
            for (List<String> row : rows) {
                colVals.add(row.get(i));
            }
            colData.put(colName, colVals);
        }
        for (DatasetColumn c : columns) {
            colMeta.put(c.getColumnName(), c);
        }

        List<Association> associations = new ArrayList<>();
        if (columns == null || columns.size() < 2) {
            return associations;
        }
        int numCols = columns.size();

        for (int i = 0; i < numCols; i++) {
            DatasetColumn colA = columns.get(i);
            String nameA = colA != null ? colA.getColumnName() : null;
            String typeA = colA != null ? colA.getDataType() : null;
            List<String> rawA = nameA != null ? colData.get(nameA) : null;

            for (int j = i + 1; j < numCols; j++) {
                DatasetColumn colB = columns.get(j);
                String nameB = colB != null ? colB.getColumnName() : null;
                String typeB = colB != null ? colB.getDataType() : null;
                List<String> rawB = nameB != null ? colData.get(nameB) : null;

                if (typeA == null || typeB == null || rawA == null || rawB == null) continue;

                // Skip non-informative ID columns (IDENTIFIER, 100% unique strings, or single constant values)
                if ("IDENTIFIER".equals(typeA) || "IDENTIFIER".equals(typeB)) continue;
                int uA = colA.getUniqueCount() != null ? colA.getUniqueCount() : 0;
                int uB = colB.getUniqueCount() != null ? colB.getUniqueCount() : 0;
                if (uA <= 1 || uB <= 1) continue;
                if (("TEXT".equals(typeA) && uA > 50) || ("TEXT".equals(typeB) && uB > 50)) continue;

                StatisticalTestEngine.TestResult result = null;

                try {
                    if ("NUMERIC".equals(typeA) && "NUMERIC".equals(typeB)) {
                        result = testEngine.computePearsonCorrelation(nameA, nameB, rawA, rawB);
                    } else if ("NUMERIC".equals(typeA) && ("CATEGORICAL".equals(typeB) || "BOOLEAN".equals(typeB))) {
                        if (uB == 2) {
                            result = testEngine.computeWelchTTest(nameA, nameB, rawA, rawB);
                        } else if (uB > 2 && uB <= 20) {
                            result = testEngine.computeOneWayAnova(nameA, nameB, rawA, rawB);
                        }
                    } else if (("CATEGORICAL".equals(typeA) || "BOOLEAN".equals(typeA)) && "NUMERIC".equals(typeB)) {
                        if (uA == 2) {
                            result = testEngine.computeWelchTTest(nameB, nameA, rawB, rawA);
                        } else if (uA > 2 && uA <= 20) {
                            result = testEngine.computeOneWayAnova(nameB, nameA, rawB, rawA);
                        }
                    } else if (("CATEGORICAL".equals(typeA) || "BOOLEAN".equals(typeA)) &&
                            ("CATEGORICAL".equals(typeB) || "BOOLEAN".equals(typeB))) {
                        if (uA <= 25 && uB <= 25) {
                            result = testEngine.computeChiSquare(nameA, nameB, rawA, rawB);
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
}
