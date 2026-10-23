package com.autoscientist.analysis.profiling;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DatasetProfiler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class ProfilingResult {
        private final List<DatasetColumn> columns;
        private final int rowCount;
        private final int columnCount;
        private final String datasetType;

        public ProfilingResult(List<DatasetColumn> columns, int rowCount, int columnCount, String datasetType) {
            this.columns = columns;
            this.rowCount = rowCount;
            this.columnCount = columnCount;
            this.datasetType = datasetType;
        }

        public List<DatasetColumn> getColumns() {
            return columns;
        }

        public int getRowCount() {
            return rowCount;
        }

        public int getColumnCount() {
            return columnCount;
        }

        public String getDatasetType() {
            return datasetType;
        }
    }

    public ProfilingResult profile(InputStream inputStream, Dataset dataset) throws Exception {
        return profile(inputStream, dataset, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * @param forceIdentifier    column names the user explicitly wants treated as identifiers
     * @param forceNonIdentifier column names the user explicitly wants treated as analysable
     *                           (overrides an automatic identifier guess — explicit inclusion)
     */
    public ProfilingResult profile(InputStream inputStream, Dataset dataset,
                                   Set<String> forceIdentifier, Set<String> forceNonIdentifier) throws Exception {
        Set<String> forceId = normalizeOverrideNames(forceIdentifier);
        Set<String> forceNonId = normalizeOverrideNames(forceNonIdentifier);
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
                List<String> rowValues = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    rowValues.add(record.get(i));
                }
                rows.add(rowValues);
            }
        }

        int totalRows = rows.size();
        int totalCols = headers.size();
        List<DatasetColumn> columns = new ArrayList<>();

        int numericCount = 0;
        int categoricalCount = 0;
        int dateTimeCount = 0;

        for (int colIdx = 0; colIdx < totalCols; colIdx++) {
            String colName = headers.get(colIdx);
            List<String> rawValues = new ArrayList<>();
            for (List<String> row : rows) {
                rawValues.add(row.get(colIdx));
            }

            // Missing values analysis
            int missingCount = 0;
            List<String> nonNullValues = new ArrayList<>();
            for (String val : rawValues) {
                if (val == null || val.trim().isEmpty() || val.equalsIgnoreCase("na") ||
                        val.equalsIgnoreCase("null") || val.equalsIgnoreCase("nan") || val.equalsIgnoreCase("none")) {
                    missingCount++;
                } else {
                    nonNullValues.add(val.trim());
                }
            }

            double missingPct = totalRows > 0 ? (double) missingCount / totalRows * 100.0 : 0.0;
            Set<String> uniqueSet = new HashSet<>(nonNullValues);
            int uniqueCount = uniqueSet.size();

            // Type inference
            TypeInference inference = inferColumnType(colName, nonNullValues, uniqueCount, totalRows);
            String dataType = inference.type;
            double confidence = inference.confidence;

            // Explicit user overrides win over the automatic guess.
            String normalizedCol = normalizeName(colName);
            if (forceId.contains(normalizedCol)) {
                dataType = "IDENTIFIER";
                confidence = 1.0;
            } else if (forceNonId.contains(normalizedCol) && "IDENTIFIER".equals(dataType)) {
                dataType = inference.numeric ? "NUMERIC" : "CATEGORICAL";
                confidence = 0.9;
            }

            if ("NUMERIC".equals(dataType)) numericCount++;
            else if ("CATEGORICAL".equals(dataType) || "BOOLEAN".equals(dataType)) categoricalCount++;
            else if ("DATE_TIME".equals(dataType)) dateTimeCount++;

            DatasetColumn column = DatasetColumn.builder()
                    .dataset(dataset)
                    .columnIndex(colIdx)
                    .columnName(colName)
                    .dataType(dataType)
                    .typeConfidence(confidence)
                    .missingCount(missingCount)
                    .missingPct(Math.round(missingPct * 100.0) / 100.0)
                    .uniqueCount(uniqueCount)
                    .build();

            if ("IDENTIFIER".equals(dataType)) {
                // Identifiers carry no analytic summary: mean/median/std of an id column is
                // meaningless and must never surface in statistics, correlations or reports.
                // uniqueCount/missing counts are already set on the column above.
            } else if (dataType.equals("NUMERIC")) {
                computeNumericStats(column, nonNullValues);
            } else {
                computeCategoricalStats(column, nonNullValues);
            }

            columns.add(column);
        }

        // Infer dataset macro type
        String datasetType = inferDatasetType(numericCount, categoricalCount, dateTimeCount, totalCols);

        return new ProfilingResult(columns, totalRows, totalCols, datasetType);
    }

    private Set<String> normalizeOverrideNames(Set<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (String n : names) {
            if (n != null && !n.isBlank()) out.add(normalizeName(n));
        }
        return out;
    }

    private static class TypeInference {
        String type;
        double confidence;
        boolean numeric;

        TypeInference(String type, double confidence, boolean numeric) {
            this.type = type;
            this.confidence = confidence;
            this.numeric = numeric;
        }
    }

    /**
     * Normalised form: lowercase, runs of non-alphanumeric characters become a single "_",
     * leading/trailing "_" stripped. "Zip Code" -> "zip_code", "customer-ID" -> "customer_id".
     */
    private String normalizeName(String colName) {
        if (colName == null) return "";
        String cleaned = colName.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_");
        cleaned = cleaned.replaceAll("^_+", "").replaceAll("_+$", "");
        return cleaned;
    }

    private static final Set<String> EXACT_IDENTIFIER_NAMES = new HashSet<>(Arrays.asList(
            "id", "ids", "uuid", "guid", "index", "row_id", "row_num", "rowid", "s_no", "sno",
            "pk", "primary_key", "foreign_key", "key", "unique_key", "entity_key", "record_key",
            "zip", "zip_code", "zipcode", "postal", "postal_code", "postalcode", "pin", "pin_code", "pincode",
            "phone", "phone_number", "phonenumber", "telephone", "mobile", "mobile_number", "mobilenumber", "msisdn",
            "ssn", "social_security_number", "email", "e_mail", "contact_email",
            "sku", "upc", "isbn", "ean", "barcode", "gtin",
            "account_number", "account_no", "accountnumber", "invoice_number", "invoice_no", "invoicenumber",
            "receipt_number", "receipt_no", "order_number", "order_no", "ordernumber",
            "record_number", "record_no", "recordnumber", "customer_number", "customer_no",
            "patient_number", "patient_no", "student_number", "student_no", "roll_no", "roll_number",
            "registration_number", "reg_no", "license_number", "licence_number", "passport_number",
            "national_id", "tax_id", "taxid", "credit_card", "card_number", "iban", "routing_number",
            "serial", "serial_number", "serialnumber", "transaction_number", "ticket_number",
            "claim_number", "policy_number", "member_number", "employee_number", "house_number"
    ));

    private static final Set<String> ID_TOKENS = new HashSet<>(Arrays.asList(
            "id", "ids", "no", "num", "number", "uuid", "guid", "code"
    ));

    private static final Set<String> ENTITY_TOKENS = new HashSet<>(Arrays.asList(
            "customer", "user", "users", "patient", "students", "student", "account", "order",
            "transaction", "claim", "record", "invoice", "bill", "ticket", "case", "member",
            "employee", "staff", "subject", "participant", "booking", "reservation", "shipment",
            "policy", "contract", "visit", "admission", "receipt", "serial", "roll"
    ));

    private boolean isIdentifierName(String colName) {
        if (colName == null || colName.trim().isEmpty()) return false;
        // CamelCase "customerId"/"orderId" style
        if (colName.length() > 2 && Character.isUpperCase(colName.charAt(colName.length() - 1))
                && colName.endsWith("Id")) {
            return true;
        }
        String normalized = normalizeName(colName);
        if (normalized.isEmpty()) return false;
        if (EXACT_IDENTIFIER_NAMES.contains(normalized)) return true;
        if (normalized.endsWith("_id") || normalized.startsWith("id_") || normalized.equals("id")) {
            return true;
        }
        String[] tokens = normalized.split("_");
        String last = tokens[tokens.length - 1];
        // record-number style: s_no, order_no, invoice_number, row_index
        if ((last.equals("no") || last.equals("num") || last.equals("number")) && tokens.length >= 2) {
            return true;
        }
        // entity + identifier token pairs: customer_code, patient_key, user_uuid
        if (tokens.length >= 2 && ID_TOKENS.contains(last) && ENTITY_TOKENS.contains(tokens[0])) {
            return true;
        }
        // legacy broad contains-based matches (kept for backward compatibility)
        return normalized.contains("customer_id") || normalized.contains("customerid")
                || normalized.contains("user_id") || normalized.contains("userid")
                || normalized.contains("record_id") || normalized.contains("recordid")
                || normalized.contains("transaction_id") || normalized.contains("transactionid")
                || normalized.contains("claim_id") || normalized.contains("claimid")
                || normalized.contains("patient_id") || normalized.contains("patientid")
                || normalized.contains("student_id") || normalized.contains("studentid")
                || normalized.contains("account_id") || normalized.contains("accountid")
                || normalized.contains("order_id") || normalized.contains("orderid");
    }

    private boolean isAllIntegerOrText(List<String> values) {
        for (String v : values) {
            if (v == null || v.isEmpty()) continue;
            try {
                double d = Double.parseDouble(v);
                if (d != Math.floor(d)) {
                    return false; // fractional decimal -> continuous analytical variable
                }
            } catch (NumberFormatException ignored) {
                // Alphanumeric code or text ID
            }
        }
        return true;
    }

    /**
     * Value-based identifier detection for columns whose names give no hint:
     * fully (or near fully) unique, all-integer values that form a strictly
     * increasing/decreasing sequence (record numbers such as 1001..1060).
     * Deliberately conservative so genuine measurements are never reclassified.
     */
    private boolean looksLikeSequentialRecordNumber(List<String> values, int uniqueCount) {
        int n = values.size();
        if (n < 10 || uniqueCount < 10) return false;
        double distinctRatio = (double) uniqueCount / n;
        if (distinctRatio < 0.98) return false;

        List<Double> nums = new ArrayList<>();
        for (String v : values) {
            try {
                double d = Double.parseDouble(v);
                if (d != Math.floor(d)) return false;
                nums.add(d);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        Collections.sort(nums);
        int steps = 0;
        for (int i = 1; i < nums.size(); i++) {
            if (nums.get(i) - nums.get(i - 1) == 1.0) steps++;
        }
        double sequentialFraction = (double) steps / (nums.size() - 1);
        return sequentialFraction >= 0.85;
    }

    private TypeInference inferColumnType(String colName, List<String> values, int uniqueCount, int totalRows) {
        if (values.isEmpty()) {
            return new TypeInference("UNKNOWN", 0.5, false);
        }

        int numericMatches = 0;
        int boolMatches = 0;
        int dateMatches = 0;
        int longTextMatches = 0;

        for (String v : values) {
            if (isNumeric(v)) {
                numericMatches++;
            }
            if (isBoolean(v)) {
                boolMatches++;
            }
            if (isDateOrTime(v)) {
                dateMatches++;
            }
            if (v.length() > 60) {
                longTextMatches++;
            }
        }

        int n = values.size();
        double numRatio = (double) numericMatches / n;
        double boolRatio = (double) boolMatches / n;
        double dateRatio = (double) dateMatches / n;
        double distinctRatio = (double) uniqueCount / n;
        boolean matchesIdName = isIdentifierName(colName);

        // Detect identifier columns. Name patterns are a strong signal on their own —
        // they no longer depend on a uniqueness conjunct that short/low-cardinality
        // files used to defeat. Values must still be integer-like (or alphanumeric).
        if (matchesIdName && isAllIntegerOrText(values)) {
            return new TypeInference("IDENTIFIER", 0.99, numRatio >= 0.85);
        }

        // Name gives no hint, but values are unique, integer and sequential → record number.
        if (numRatio >= 0.90 && looksLikeSequentialRecordNumber(values, uniqueCount)) {
            return new TypeInference("IDENTIFIER", 0.90, true);
        }

        if (numRatio >= 0.90) {
            // Check if it's binary numeric 0/1 with exactly 2 unique values
            if (uniqueCount <= 2 && (values.stream().allMatch(v -> v.equals("0") || v.equals("1")))) {
                return new TypeInference("BOOLEAN", 0.95, true);
            }
            return new TypeInference("NUMERIC", numRatio, true);
        }

        if (boolRatio >= 0.95) {
            return new TypeInference("BOOLEAN", boolRatio, false);
        }

        if (dateRatio >= 0.85) {
            return new TypeInference("DATE_TIME", dateRatio, false);
        }

        if ((double) longTextMatches / n > 0.40) {
            return new TypeInference("TEXT", 0.90, false);
        }

        // Distinct ratio
        if (distinctRatio < 0.35 || uniqueCount <= 50) {
            return new TypeInference("CATEGORICAL", 0.95, false);
        }

        return new TypeInference("TEXT", 0.80, false);
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBoolean(String str) {
        String s = str.toLowerCase();
        return s.equals("true") || s.equals("false") || s.equals("yes") || s.equals("no") || s.equals("t") || s.equals("f");
    }

    private boolean isDateOrTime(String str) {
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                LocalDate.parse(str, fmt);
                return true;
            } catch (Exception ignored) {}
            try {
                LocalDateTime.parse(str, fmt);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void computeNumericStats(DatasetColumn column, List<String> values) throws Exception {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (String v : values) {
            try {
                stats.addValue(Double.parseDouble(v));
            } catch (NumberFormatException ignored) {}
        }

        if (stats.getN() > 0) {
            column.setMinVal(stats.getMin());
            column.setMaxVal(stats.getMax());
            column.setMeanVal(stats.getMean());
            column.setMedianVal(stats.getPercentile(50));
            column.setStdDev(stats.getStandardDeviation());
            column.setVariance(stats.getVariance());
            column.setQ1(stats.getPercentile(25));
            column.setQ3(stats.getPercentile(75));
            column.setSkewness(Double.isNaN(stats.getSkewness()) ? 0.0 : stats.getSkewness());

            // Build histogram bins
            double min = stats.getMin();
            double max = stats.getMax();
            int numBins = 10;
            double binWidth = (max > min) ? (max - min) / numBins : 1.0;

            Map<String, Object> distMap = new LinkedHashMap<>();
            List<String> binLabels = new ArrayList<>();
            List<Integer> binCounts = new ArrayList<>();

            double[] binEdges = new double[numBins + 1];
            for (int i = 0; i <= numBins; i++) {
                binEdges[i] = min + i * binWidth;
            }

            int[] counts = new int[numBins];
            double[] allValues = stats.getValues();
            for (double val : allValues) {
                int binIndex = (int) ((val - min) / binWidth);
                if (binIndex >= numBins) binIndex = numBins - 1;
                if (binIndex < 0) binIndex = 0;
                counts[binIndex]++;
            }

            for (int i = 0; i < numBins; i++) {
                binLabels.add(String.format("%.2f - %.2f", binEdges[i], binEdges[i + 1]));
                binCounts.add(counts[i]);
            }

            distMap.put("labels", binLabels);
            distMap.put("counts", binCounts);
            column.setDistributionJson(objectMapper.writeValueAsString(distMap));
        }
    }

    private void computeCategoricalStats(DatasetColumn column, List<String> values) throws Exception {
        Map<String, Integer> freqMap = new LinkedHashMap<>();
        for (String v : values) {
            freqMap.put(v, freqMap.getOrDefault(v, 0) + 1);
        }

        // Sort by frequency descending and take top 20
        Map<String, Integer> topCategories = freqMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        column.setCategoriesJson(objectMapper.writeValueAsString(topCategories));
    }

    public String inferDatasetType(int numericCount, int categoricalCount, int dateTimeCount, int totalCols) {
        if (dateTimeCount >= 1 && (double) dateTimeCount / totalCols >= 0.15) {
            return "TIME_SERIES";
        }
        if ((double) numericCount / totalCols >= 0.80) {
            return "REGRESSION";
        }
        if ((double) categoricalCount / totalCols >= 0.60) {
            return "CLASSIFICATION";
        }
        return "MIXED";
    }
}
