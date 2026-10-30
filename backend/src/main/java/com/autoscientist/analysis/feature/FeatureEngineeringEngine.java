package com.autoscientist.analysis.feature;

import com.autoscientist.entity.DatasetColumn;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class FeatureEngineeringEngine {

    /** Fixed shuffle seed: identical input + identical seed => identical split (reproducible runs). */
    public static final long RANDOM_SEED = 42L;

    public static class DatasetSplit {
        private final double[][] XTrain;
        private final double[] yTrain;
        private final double[][] XVal;
        private final double[] yVal;
        private final double[][] XTest;
        private final double[] yTest;
        private final List<String> featureNames;
        private final String targetColumn;
        private final String problemType; // REGRESSION, CLASSIFICATION
        private final Map<String, Integer> classMap; // for classification
        private final Map<Integer, String> reverseClassMap;
        private final Map<String, Object> preprocessingConfig;

        public DatasetSplit(double[][] XTrain, double[] yTrain,
                            double[][] XVal, double[] yVal,
                            double[][] XTest, double[] yTest,
                            List<String> featureNames, String targetColumn,
                            String problemType, Map<String, Integer> classMap,
                            Map<Integer, String> reverseClassMap,
                            Map<String, Object> preprocessingConfig) {
            this.XTrain = XTrain;
            this.yTrain = yTrain;
            this.XVal = XVal;
            this.yVal = yVal;
            this.XTest = XTest;
            this.yTest = yTest;
            this.featureNames = featureNames;
            this.targetColumn = targetColumn;
            this.problemType = problemType;
            this.classMap = classMap;
            this.reverseClassMap = reverseClassMap;
            this.preprocessingConfig = preprocessingConfig;
        }

        public double[][] getXTrain() { return XTrain; }
        public double[] getYTrain() { return yTrain; }
        public double[][] getXVal() { return XVal; }
        public double[] getYVal() { return yVal; }
        public double[][] getXTest() { return XTest; }
        public double[] getYTest() { return yTest; }
        public List<String> getFeatureNames() { return featureNames; }
        public String getTargetColumn() { return targetColumn; }
        public String getProblemType() { return problemType; }
        public Map<String, Integer> getClassMap() { return classMap; }
        public Map<Integer, String> getReverseClassMap() { return reverseClassMap; }
        public Map<String, Object> getPreprocessingConfig() { return preprocessingConfig; }
    }

    public DatasetSplit prepareDataset(InputStream inputStream, List<DatasetColumn> columns,
                                       String targetColumn, String requestedProblemType) throws Exception {

        List<String> headers;
        List<List<String>> allRows = new ArrayList<>();

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
                List<String> rowValues = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    rowValues.add(record.get(i));
                }
                allRows.add(rowValues);
            }
        }

        int targetIndex = indexOfIgnoreCase(headers, targetColumn);
        if (targetIndex < 0) {
            throw new IllegalArgumentException("Target column not found in dataset: " + targetColumn);
        }
        // Canonical header spelling as it exists in the file (case-insensitive lookup above).
        String resolvedTarget = headers.get(targetIndex);

        // Determine problem type
        final String resolvedTargetFinal = resolvedTarget;
        DatasetColumn targetColMeta = columns.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(resolvedTargetFinal))
                .findFirst().orElse(null);

        String problemType = requestedProblemType;
        if (problemType == null || problemType.equals("AUTO")) {
            if (targetColMeta != null && targetColMeta.getDataType().equals("NUMERIC") && targetColMeta.getUniqueCount() > 10) {
                problemType = "REGRESSION";
            } else {
                problemType = "CLASSIFICATION";
            }
        }

        // Feature columns (excluding target, IDENTIFIER columns, and high-cardinality text)
        List<DatasetColumn> featureColumns = new ArrayList<>();
        for (DatasetColumn col : columns) {
            if (!col.getColumnName().equalsIgnoreCase(resolvedTarget) && col.getUniqueCount() > 1 &&
                    !col.getDataType().equals("IDENTIFIER") &&
                    !(col.getDataType().equals("TEXT") && col.getUniqueCount() > 50)) {
                featureColumns.add(col);
            }
        }

        // Shuffle rows deterministically with fixed seed
        Collections.shuffle(allRows, new Random(RANDOM_SEED));

        int totalN = allRows.size();
        if (totalN == 0) {
            throw new IllegalArgumentException("Dataset has no data rows to split.");
        }

        // Disjoint 70/15/15 split. Tiny datasets cannot support all three partitions,
        // so they degrade to train-only (1 row) or train+test (2 rows) rather than
        // reusing the same rows for validation/testing (which would leak the training
        // targets into the reported metrics).
        int trainN;
        int valN;
        if (totalN <= 2) {
            trainN = 1;
            valN = 0;
        } else {
            trainN = (int) Math.round(totalN * 0.70);
            valN = (int) Math.round(totalN * 0.15);
            if (trainN < 1) trainN = 1;
            if (trainN + valN >= totalN) {
                valN = Math.min(valN, Math.max(0, totalN - 2));
                trainN = totalN - valN - 1;
            }
        }
        int testN = totalN - trainN - valN;

        List<List<String>> trainRows = allRows.subList(0, trainN);
        List<List<String>> valRows = valN > 0
                ? allRows.subList(trainN, trainN + valN)
                : Collections.emptyList();
        List<List<String>> testRows = testN > 0
                ? allRows.subList(trainN + valN, totalN)
                : Collections.emptyList();

        // 1. Compute Train-set imputation values (prevents data leakage)
        Map<String, Double> numMedians = new HashMap<>();
        Map<String, String> catModes = new HashMap<>();
        Map<String, List<String>> oneHotCategories = new HashMap<>();

        for (DatasetColumn col : featureColumns) {
            int colIdx = headers.indexOf(col.getColumnName());
            if (col.getDataType().equals("NUMERIC")) {
                DescriptiveStatistics stats = new DescriptiveStatistics();
                for (List<String> r : trainRows) {
                    try {
                        String s = r.get(colIdx);
                        if (isValidNumber(s)) stats.addValue(Double.parseDouble(s.trim()));
                    } catch (Exception ignored) {}
                }
                numMedians.put(col.getColumnName(), stats.getN() > 0 ? stats.getPercentile(50) : 0.0);
            } else {
                Map<String, Integer> freq = new HashMap<>();
                for (List<String> r : trainRows) {
                    String s = r.get(colIdx);
                    if (s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase("na")) {
                        freq.merge(s.trim(), 1, Integer::sum);
                    }
                }
                String mode = freq.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("UNKNOWN");
                catModes.put(col.getColumnName(), mode);

                // Identify top categories to one-hot encode
                List<String> topCats = new ArrayList<>(freq.keySet());
                Collections.sort(topCats);
                oneHotCategories.put(col.getColumnName(), topCats);
            }
        }

        // Build feature headers list
        List<String> transformedFeatureNames = new ArrayList<>();
        for (DatasetColumn col : featureColumns) {
            if (col.getDataType().equals("NUMERIC")) {
                transformedFeatureNames.add(col.getColumnName());
            } else {
                List<String> cats = oneHotCategories.get(col.getColumnName());
                for (String cat : cats) {
                    transformedFeatureNames.add(col.getColumnName() + "_" + cat);
                }
            }
        }

        // 2. Target mapping (if classification)
        Map<String, Integer> classMap = new LinkedHashMap<>();
        Map<Integer, String> reverseClassMap = new LinkedHashMap<>();

        if (problemType.equals("CLASSIFICATION")) {
            Set<String> trainClasses = new TreeSet<>();
            for (List<String> r : trainRows) {
                String yVal = r.get(targetIndex);
                if (yVal != null && !yVal.trim().isEmpty() && !yVal.equalsIgnoreCase("na")) {
                    trainClasses.add(yVal.trim());
                }
            }
            int cIdx = 0;
            for (String c : trainClasses) {
                classMap.put(c, cIdx);
                reverseClassMap.put(cIdx, c);
                cIdx++;
            }
        }

        // Transform matrices
        MatrixData trainData = transformRows(trainRows, headers, targetIndex, featureColumns,
                numMedians, catModes, oneHotCategories, classMap, problemType);
        MatrixData valData = transformRows(valRows, headers, targetIndex, featureColumns,
                numMedians, catModes, oneHotCategories, classMap, problemType);
        MatrixData testData = transformRows(testRows, headers, targetIndex, featureColumns,
                numMedians, catModes, oneHotCategories, classMap, problemType);

        // 3. StandardScaler fitted exclusively on Train data
        int numFeats = transformedFeatureNames.size();
        double[] mean = new double[numFeats];
        double[] std = new double[numFeats];

        for (int j = 0; j < numFeats; j++) {
            DescriptiveStatistics st = new DescriptiveStatistics();
            for (int i = 0; i < trainData.X.length; i++) {
                st.addValue(trainData.X[i][j]);
            }
            mean[j] = st.getMean();
            std[j] = (st.getStandardDeviation() > 1e-6) ? st.getStandardDeviation() : 1.0;
        }

        scaleMatrix(trainData.X, mean, std);
        scaleMatrix(valData.X, mean, std);
        scaleMatrix(testData.X, mean, std);

        Map<String, Object> preprocConfig = new HashMap<>();
        preprocConfig.put("scaler", "StandardScaler");
        preprocConfig.put("imputation", "Median/Mode (Train Fitted)");
        preprocConfig.put("encoding", "One-Hot Categorical");
        preprocConfig.put("trainSize", trainRows.size());
        preprocConfig.put("valSize", valRows.size());
        preprocConfig.put("testSize", testRows.size());
        preprocConfig.put("featureCount", transformedFeatureNames.size());
        preprocConfig.put("randomSeed", RANDOM_SEED);
        preprocConfig.put("splitRatio", "70/15/15");
        preprocConfig.put("resolvedTarget", resolvedTarget);
        preprocConfig.put("smallSample", totalN < 5);
        if (totalN < 5) {
            preprocConfig.put("splitWarning", "Only " + totalN + " usable row(s): train/validation/test "
                    + "partitions are disjoint but too small for reliable metrics.");
        }

        // Expose the fitted transformation so downstream consumers (e.g. the what-if
        // simulator) can encode RAW user inputs exactly the way training did,
        // instead of treating raw values as already-standardized numbers.
        List<Double> meanList = new ArrayList<>();
        List<Double> stdList = new ArrayList<>();
        for (int j = 0; j < numFeats; j++) {
            meanList.add(mean[j]);
            stdList.add(std[j]);
        }
        preprocConfig.put("scalerMean", meanList);
        preprocConfig.put("scalerStd", stdList);
        preprocConfig.put("rawFeatures", buildRawFeatureSchema(featureColumns, headers, trainRows,
                numMedians, catModes, oneHotCategories));

        return new DatasetSplit(
                trainData.X, trainData.y,
                valData.X, valData.y,
                testData.X, testData.y,
                transformedFeatureNames, resolvedTarget,
                problemType, classMap, reverseClassMap,
                preprocConfig
        );
    }

    /**
     * Describes every raw (untransformed) feature so a user-supplied scenario can be
     * encoded exactly like the training matrix: imputation, one-hot expansion and
     * standardisation are all described here.
     */
    private List<Map<String, Object>> buildRawFeatureSchema(List<DatasetColumn> featureColumns,
                                                              List<String> headers,
                                                              List<List<String>> trainRows,
                                                              Map<String, Double> numMedians,
                                                              Map<String, String> catModes,
                                                              Map<String, List<String>> oneHotCategories) {
        List<Map<String, Object>> schema = new ArrayList<>();
        for (DatasetColumn col : featureColumns) {
            int colIdx = headers.indexOf(col.getColumnName());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", col.getColumnName());
            if (col.getDataType().equals("NUMERIC")) {
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (List<String> r : trainRows) {
                    String s = (colIdx >= 0 && colIdx < r.size()) ? r.get(colIdx) : null;
                    if (isValidNumber(s)) {
                        double v = Double.parseDouble(s.trim());
                        min = Math.min(min, v);
                        max = Math.max(max, v);
                    }
                }
                entry.put("kind", "NUMERIC");
                entry.put("imputeValue", numMedians.get(col.getColumnName()));
                entry.put("min", (min == Double.MAX_VALUE) ? null : min);
                entry.put("max", (max == -Double.MAX_VALUE) ? null : max);
                entry.put("categories", null);
                entry.put("transformedNames", List.of(col.getColumnName()));
            } else {
                List<String> cats = oneHotCategories.get(col.getColumnName());
                List<String> transformed = new ArrayList<>();
                if (cats != null) {
                    for (String c : cats) transformed.add(col.getColumnName() + "_" + c);
                }
                entry.put("kind", "CATEGORICAL");
                entry.put("imputeValue", catModes.get(col.getColumnName()));
                entry.put("min", null);
                entry.put("max", null);
                entry.put("categories", cats);
                entry.put("transformedNames", transformed);
            }
            schema.add(entry);
        }
        return schema;
    }

    private int indexOfIgnoreCase(List<String> headers, String name) {
        if (name == null) return -1;
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(name.trim())) return i;
        }
        return -1;
    }

    private static class MatrixData {
        double[][] X;
        double[] y;
        MatrixData(double[][] X, double[] y) {
            this.X = X;
            this.y = y;
        }
    }

    private MatrixData transformRows(List<List<String>> rows, List<String> headers, int targetIndex,
                                     List<DatasetColumn> featureColumns, Map<String, Double> numMedians,
                                     Map<String, String> catModes, Map<String, List<String>> oneHotCategories,
                                     Map<String, Integer> classMap, String problemType) {

        List<double[]> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();

        for (List<String> row : rows) {
            String rawY = row.get(targetIndex);
            Double targetVal = null;
            if (problemType.equals("CLASSIFICATION")) {
                if (rawY != null && classMap.containsKey(rawY.trim())) {
                    targetVal = (double) classMap.get(rawY.trim());
                }
            } else {
                if (isValidNumber(rawY)) {
                    targetVal = Double.parseDouble(rawY.trim());
                }
            }

            if (targetVal == null) continue; // skip rows with missing target in supervised training

            List<Double> featVals = new ArrayList<>();
            for (DatasetColumn col : featureColumns) {
                int colIdx = headers.indexOf(col.getColumnName());
                String rawVal = row.get(colIdx);

                if (col.getDataType().equals("NUMERIC")) {
                    double v = isValidNumber(rawVal) ? Double.parseDouble(rawVal.trim()) : numMedians.get(col.getColumnName());
                    featVals.add(v);
                } else {
                    String v = (rawVal != null && !rawVal.trim().isEmpty() && !rawVal.equalsIgnoreCase("na"))
                            ? rawVal.trim() : catModes.get(col.getColumnName());
                    List<String> cats = oneHotCategories.get(col.getColumnName());
                    for (String cat : cats) {
                        featVals.add(v.equals(cat) ? 1.0 : 0.0);
                    }
                }
            }

            double[] xArr = new double[featVals.size()];
            for (int k = 0; k < featVals.size(); k++) xArr[k] = featVals.get(k);

            xList.add(xArr);
            yList.add(targetVal);
        }

        double[][] X = new double[xList.size()][];
        double[] y = new double[yList.size()];
        for (int i = 0; i < xList.size(); i++) {
            X[i] = xList.get(i);
            y[i] = yList.get(i);
        }

        return new MatrixData(X, y);
    }

    private void scaleMatrix(double[][] X, double[] mean, double[] std) {
        for (double[] row : X) {
            for (int j = 0; j < row.length; j++) {
                row[j] = (row[j] - mean[j]) / std[j];
            }
        }
    }

    private boolean isValidNumber(String s) {
        if (s == null || s.trim().isEmpty() || s.equalsIgnoreCase("na") || s.equalsIgnoreCase("nan") || s.equalsIgnoreCase("null")) {
            return false;
        }
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
