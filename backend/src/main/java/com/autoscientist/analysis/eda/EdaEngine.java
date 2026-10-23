package com.autoscientist.analysis.eda;

import com.autoscientist.analysis.statistics.StatisticalTestEngine;
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
import java.util.stream.Collectors;

@Component
public class EdaEngine {

    private final StatisticalTestEngine statEngine;

    public EdaEngine(StatisticalTestEngine statEngine) {
        this.statEngine = statEngine;
    }

    public static class CorrelationMatrix {
        private final List<String> variables;
        private final double[][] matrix;
        /** Two-sided p-value of each pairwise Pearson test (null hypothesis: rho = 0). */
        private final double[][] pValues;
        /** Paired (complete-case) sample size used for each pair. */
        private final int[] sampleSizes;

        public CorrelationMatrix(List<String> variables, double[][] matrix) {
            this(variables, matrix, null, null);
        }

        public CorrelationMatrix(List<String> variables, double[][] matrix, double[][] pValues, int[] sampleSizes) {
            this.variables = variables;
            this.matrix = matrix;
            this.pValues = pValues;
            this.sampleSizes = sampleSizes;
        }

        public List<String> getVariables() { return variables; }
        public double[][] getMatrix() { return matrix; }
        public double[][] getPValues() { return pValues; }
        public int[] getSampleSizes() { return sampleSizes; }
    }

    public static class BoxPlotData {
        private final String variable;
        private final double min;
        private final double q1;
        private final double median;
        private final double q3;
        private final double max;
        private final double iqr;
        private final List<Double> outliers;

        public BoxPlotData(String variable, double min, double q1, double median, double q3, double max, double iqr, List<Double> outliers) {
            this.variable = variable;
            this.min = min;
            this.q1 = q1;
            this.median = median;
            this.q3 = q3;
            this.max = max;
            this.iqr = iqr;
            this.outliers = outliers;
        }

        public String getVariable() { return variable; }
        public double getMin() { return min; }
        public double getQ1() { return q1; }
        public double getMedian() { return median; }
        public double getQ3() { return q3; }
        public double getMax() { return max; }
        public double getIqr() { return iqr; }
        public List<Double> getOutliers() { return outliers; }
    }

    public static class GroupSummary {
        private final String groupName;
        private final int count;
        private final double mean;
        private final double stdDev;
        private final double median;
        private final double min;
        private final double max;

        public GroupSummary(String groupName, int count, double mean, double stdDev, double median, double min, double max) {
            this.groupName = groupName;
            this.count = count;
            this.mean = mean;
            this.stdDev = stdDev;
            this.median = median;
            this.min = min;
            this.max = max;
        }

        public String getGroupName() { return groupName; }
        public int count() { return count; }
        public double getMean() { return mean; }
        public double getStdDev() { return stdDev; }
        public double getMedian() { return median; }
        public double getMin() { return min; }
        public double getMax() { return max; }
    }

    /**
     * A single clear predicate: only columns typed NUMERIC enter numeric analysis.
     * IDENTIFIER columns are never typed NUMERIC, so this is the one place that decides.
     */
    private boolean isAnalyticNumeric(DatasetColumn c) {
        return "NUMERIC".equals(c.getDataType());
    }

    private boolean isAnalyticCategorical(DatasetColumn c) {
        String t = c.getDataType();
        return "CATEGORICAL".equals(t) || "BOOLEAN".equals(t) || "DATE_TIME".equals(t);
    }

    public CorrelationMatrix computeCorrelationMatrix(InputStream inputStream, List<DatasetColumn> columns) throws Exception {
        List<DatasetColumn> numCols = columns.stream()
                .filter(this::isAnalyticNumeric)
                .collect(Collectors.toList());

        List<String> variables = numCols.stream().map(DatasetColumn::getColumnName).collect(Collectors.toList());
        int k = variables.size();
        double[][] matrix = new double[k][k];

        if (k == 0) {
            return new CorrelationMatrix(variables, matrix);
        }

        Map<String, List<String>> colData = parseColumns(inputStream, variables);
        double[][] pValues = new double[k][k];
        int[] sampleSizes = new int[k];

        for (int i = 0; i < k; i++) {
            matrix[i][i] = 1.0;
            pValues[i][i] = 0.0;
            String varI = variables.get(i);
            List<String> rawI = colData.get(varI);

            for (int j = i + 1; j < k; j++) {
                String varJ = variables.get(j);
                List<String> rawJ = colData.get(varJ);

                StatisticalTestEngine.TestResult res = statEngine.computePearsonCorrelation(varI, varJ, rawI, rawJ);
                matrix[i][j] = res.getStatistic();
                matrix[j][i] = res.getStatistic();
                pValues[i][j] = res.getPValue();
                pValues[j][i] = res.getPValue();
                sampleSizes[i] = Math.max(sampleSizes[i], res.getSampleSize());
                sampleSizes[j] = Math.max(sampleSizes[j], res.getSampleSize());
            }
        }

        return new CorrelationMatrix(variables, matrix, pValues, sampleSizes);
    }

    public List<BoxPlotData> computeBoxPlots(InputStream inputStream, List<DatasetColumn> columns) throws Exception {
        List<DatasetColumn> numCols = columns.stream()
                .filter(this::isAnalyticNumeric)
                .collect(Collectors.toList());

        List<String> variables = numCols.stream().map(DatasetColumn::getColumnName).collect(Collectors.toList());
        Map<String, List<String>> colData = parseColumns(inputStream, variables);

        List<BoxPlotData> boxPlots = new ArrayList<>();
        for (String var : variables) {
            List<String> raw = colData.get(var);
            DescriptiveStatistics stats = new DescriptiveStatistics();
            for (String s : raw) {
                try {
                    stats.addValue(Double.parseDouble(s));
                } catch (Exception ignored) {}
            }

            if (stats.getN() > 0) {
                double q1 = stats.getPercentile(25);
                double q3 = stats.getPercentile(75);
                double median = stats.getPercentile(50);
                double min = stats.getMin();
                double max = stats.getMax();
                double iqr = q3 - q1;

                List<Double> outliers = new ArrayList<>();
                if (iqr > 0) {
                    double lowerFence = q1 - 1.5 * iqr;
                    double upperFence = q3 + 1.5 * iqr;
                    for (double val : stats.getValues()) {
                        if (val < lowerFence || val > upperFence) {
                            outliers.add(val);
                        }
                    }
                }

                boxPlots.add(new BoxPlotData(var, min, q1, median, q3, max, iqr, outliers));
            }
        }

        return boxPlots;
    }

    public List<GroupSummary> computeGroupedSummary(InputStream inputStream, String numVar, String catVar) throws Exception {
        Map<String, List<String>> colData = parseColumns(inputStream, Arrays.asList(numVar, catVar));
        List<String> numRaw = colData.get(numVar);
        List<String> catRaw = colData.get(catVar);

        Map<String, DescriptiveStatistics> groupMap = new HashMap<>();
        int n = Math.min(numRaw.size(), catRaw.size());

        for (int i = 0; i < n; i++) {
            String sNum = numRaw.get(i);
            String sCat = catRaw.get(i);
            if (sNum != null && sCat != null && !sNum.trim().isEmpty() && !sCat.trim().isEmpty() &&
                    !sNum.equalsIgnoreCase("na") && !sCat.equalsIgnoreCase("na")) {
                try {
                    double v = Double.parseDouble(sNum.trim());
                    groupMap.computeIfAbsent(sCat.trim(), k -> new DescriptiveStatistics()).addValue(v);
                } catch (Exception ignored) {}
            }
        }

        List<GroupSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, DescriptiveStatistics> entry : groupMap.entrySet()) {
            DescriptiveStatistics st = entry.getValue();
            if (st.getN() > 0) {
                summaries.add(new GroupSummary(
                        entry.getKey(),
                        (int) st.getN(),
                        Math.round(st.getMean() * 100.0) / 100.0,
                        Math.round(st.getStandardDeviation() * 100.0) / 100.0,
                        Math.round(st.getPercentile(50) * 100.0) / 100.0,
                        Math.round(st.getMin() * 100.0) / 100.0,
                        Math.round(st.getMax() * 100.0) / 100.0
                ));
            }
        }

        summaries.sort(Comparator.comparing(GroupSummary::getGroupName));
        return summaries;
    }

    public static class Histogram {
        private final String variable;
        private final List<Double> binStarts;
        private final List<Double> binEnds;
        private final List<Long> counts;
        private final long nonMissing;

        public Histogram(String variable, List<Double> binStarts, List<Double> binEnds, List<Long> counts, long nonMissing) {
            this.variable = variable;
            this.binStarts = binStarts;
            this.binEnds = binEnds;
            this.counts = counts;
            this.nonMissing = nonMissing;
        }

        public String getVariable() { return variable; }
        public List<Double> getBinStarts() { return binStarts; }
        public List<Double> getBinEnds() { return binEnds; }
        public List<Long> getCounts() { return counts; }
        public long getNonMissing() { return nonMissing; }
    }

    public static class ScatterData {
        private final String xVariable;
        private final String yVariable;
        private final List<double[]> points;
        private final long totalRows;
        private final boolean downsampled;
        /** Ordinary-least-squares fit of y on x over the plotted (complete-case) rows. */
        private final LinearFit fit;

        public ScatterData(String xVariable, String yVariable, List<double[]> points, long totalRows, boolean downsampled) {
            this(xVariable, yVariable, points, totalRows, downsampled, null);
        }

        public ScatterData(String xVariable, String yVariable, List<double[]> points, long totalRows,
                          boolean downsampled, LinearFit fit) {
            this.xVariable = xVariable;
            this.yVariable = yVariable;
            this.points = points;
            this.totalRows = totalRows;
            this.downsampled = downsampled;
            this.fit = fit;
        }

        public String getXVariable() { return xVariable; }
        public String getYVariable() { return yVariable; }
        public List<double[]> getPoints() { return points; }
        public long getTotalRows() { return totalRows; }
        public boolean isDownsampled() { return downsampled; }
        public LinearFit getFit() { return fit; }
    }

    /** Least-squares line fit with the usual goodness-of-fit quantities. */
    public static class LinearFit {
        private final double slope;
        private final double intercept;
        private final double rSquared;
        private final int n;
        private final double standardError;

        public LinearFit(double slope, double intercept, double rSquared, int n, double standardError) {
            this.slope = slope;
            this.intercept = intercept;
            this.rSquared = rSquared;
            this.n = n;
            this.standardError = standardError;
        }

        public double getSlope() { return slope; }
        public double getIntercept() { return intercept; }
        public double getRSquared() { return rSquared; }
        public int getN() { return n; }
        public double getStandardError() { return standardError; }
    }

    public static class MissingnessSummary {
        private final String variable;
        private final long missingCount;
        private final long totalCount;
        private final double missingPct;

        public MissingnessSummary(String variable, long missingCount, long totalCount, double missingPct) {
            this.variable = variable;
            this.missingCount = missingCount;
            this.totalCount = totalCount;
            this.missingPct = missingPct;
        }

        public String getVariable() { return variable; }
        public long getMissingCount() { return missingCount; }
        public long getTotalCount() { return totalCount; }
        public double getMissingPct() { return missingPct; }
    }

    public static class CategoryCount {
        private final String category;
        private final long count;
        private final double pct;

        public CategoryCount(String category, long count, double pct) {
            this.category = category;
            this.count = count;
            this.pct = pct;
        }

        public String getCategory() { return category; }
        public long getCount() { return count; }
        public double getPct() { return pct; }
    }

    public static class CategoricalSummary {
        private final String variable;
        private final long uniqueCount;
        private final long missingCount;
        private final List<CategoryCount> topCategories;

        public CategoricalSummary(String variable, long uniqueCount, long missingCount, List<CategoryCount> topCategories) {
            this.variable = variable;
            this.uniqueCount = uniqueCount;
            this.missingCount = missingCount;
            this.topCategories = topCategories;
        }

        public String getVariable() { return variable; }
        public long getUniqueCount() { return uniqueCount; }
        public long getMissingCount() { return missingCount; }
        public List<CategoryCount> getTopCategories() { return topCategories; }
    }

    public static class TimeSeriesPoint {
        private final String timestamp;
        private final double value;

        public TimeSeriesPoint(String timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        public String getTimestamp() { return timestamp; }
        public double getValue() { return value; }
    }

    public static final int MAX_SCATTER_POINTS = 2000;
    public static final int MAX_HISTOGRAM_BINS = 20;
    public static final int MAX_CATEGORIES = 30;
    public static final int MAX_TIMESERIES_POINTS = 500;
    public static final int MAX_GROUP_BINS = 20;
    public static final int MAX_MISSINGNESS_MATRIX_ROWS = 250;

    public List<Histogram> computeHistograms(InputStream inputStream, List<DatasetColumn> columns) throws Exception {
        List<DatasetColumn> numCols = columns.stream()
                .filter(this::isAnalyticNumeric)
                .collect(Collectors.toList());
        List<String> variables = numCols.stream().map(DatasetColumn::getColumnName).collect(Collectors.toList());
        Map<String, List<String>> colData = parseColumns(inputStream, variables);

        List<Histogram> histograms = new ArrayList<>();
        for (String var : variables) {
            DescriptiveStatistics stats = new DescriptiveStatistics();
            for (String s : colData.get(var)) {
                try { stats.addValue(Double.parseDouble(s.trim())); } catch (Exception ignored) {}
            }
            if (stats.getN() == 0) continue;

            double min = stats.getMin();
            double max = stats.getMax();
            int bins = MAX_HISTOGRAM_BINS;
            if (max > min) {
                double raw = Math.pow(stats.getN(), 1.0 / 3.0) * 2;
                bins = Math.max(5, Math.min(MAX_HISTOGRAM_BINS, (int) Math.ceil(raw)));
            }
            long[] counts = new long[bins];
            double width = (max > min) ? (max - min) / bins : 1.0;
            for (double v : stats.getValues()) {
                int b = (max > min) ? Math.min(bins - 1, (int) ((v - min) / width)) : 0;
                counts[Math.max(0, b)]++;
            }
            List<Double> starts = new ArrayList<>(), ends = new ArrayList<>();
            List<Long> countList = new ArrayList<>();
            for (int b = 0; b < bins; b++) {
                starts.add(round2(min + b * width));
                ends.add(round2(min + (b + 1) * width));
                countList.add(counts[b]);
            }
            histograms.add(new Histogram(var, starts, ends, countList, stats.getN()));
        }
        return histograms;
    }

    public ScatterData computeScatter(InputStream inputStream, String xVar, String yVar) throws Exception {
        Map<String, List<String>> colData = parseColumns(inputStream, Arrays.asList(xVar, yVar));
        List<String> xs = colData.get(xVar);
        List<String> ys = colData.get(yVar);
        List<double[]> all = new ArrayList<>();
        int n = Math.min(xs.size(), ys.size());
        for (int i = 0; i < n; i++) {
            try {
                double x = Double.parseDouble(xs.get(i).trim());
                double y = Double.parseDouble(ys.get(i).trim());
                all.add(new double[]{x, y});
            } catch (Exception ignored) {}
        }
        boolean downsampled = all.size() > MAX_SCATTER_POINTS;
        List<double[]> points;
        if (downsampled) {
            points = new ArrayList<>();
            double step = (double) all.size() / MAX_SCATTER_POINTS;
            for (int i = 0; i < MAX_SCATTER_POINTS; i++) {
                points.add(all.get((int) (i * step)));
            }
        } else {
            points = all;
        }
        return new ScatterData(xVar, yVar, points, all.size(), downsampled, computeLinearFit(all));
    }

    /**
     * Ordinary least squares fit of y on x. Returns null when the sample is too
     * small or x has no variance (a line would be meaningless).
     */
    private LinearFit computeLinearFit(List<double[]> pts) {
        int n = pts.size();
        if (n < 3) return null;
        double meanX = 0, meanY = 0;
        for (double[] p : pts) { meanX += p[0]; meanY += p[1]; }
        meanX /= n; meanY /= n;

        double sxx = 0, syy = 0, sxy = 0;
        for (double[] p : pts) {
            double dx = p[0] - meanX;
            double dy = p[1] - meanY;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        if (sxx < 1e-12) return null;

        double slope = sxy / sxx;
        double intercept = meanY - slope * meanX;
        double rSquared = (syy < 1e-12) ? 0.0 : (sxy * sxy) / (sxx * syy);
        double ssRes = 0;
        for (double[] p : pts) {
            double pred = slope * p[0] + intercept;
            ssRes += (p[1] - pred) * (p[1] - pred);
        }
        double standardError = (n > 2) ? Math.sqrt(ssRes / (n - 2)) : 0.0;
        return new LinearFit(round4(slope), round4(intercept), round4(rSquared), n, round4(standardError));
    }

    public List<MissingnessSummary> computeMissingness(InputStream inputStream, List<DatasetColumn> columns) throws Exception {
        List<String> variables = columns.stream()
                .filter(c -> !"IDENTIFIER".equals(c.getDataType()))
                .map(DatasetColumn::getColumnName)
                .collect(Collectors.toList());
        Map<String, List<String>> colData = parseColumns(inputStream, variables);

        List<MissingnessSummary> out = new ArrayList<>();
        long totalRows = 0;
        for (String var : variables) {
            totalRows = Math.max(totalRows, colData.get(var).size());
        }
        for (String var : variables) {
            List<String> raw = colData.get(var);
            long missing = 0;
            for (String s : raw) {
                if (isMissingValue(s)) {
                    missing++;
                }
            }
            double pct = raw.isEmpty() ? 0.0 : Math.round(missing * 1000.0 / raw.size()) / 10.0;
            out.add(new MissingnessSummary(var, missing, raw.size(), pct));
        }
        out.sort(Comparator.comparingDouble(MissingnessSummary::getMissingPct).reversed());
        return out;
    }

    public List<CategoricalSummary> computeCategoricalSummaries(InputStream inputStream, List<DatasetColumn> columns) throws Exception {
        List<DatasetColumn> catCols = columns.stream()
                .filter(this::isAnalyticCategorical)
                .collect(Collectors.toList());
        List<String> variables = catCols.stream().map(DatasetColumn::getColumnName).collect(Collectors.toList());
        Map<String, List<String>> colData = parseColumns(inputStream, variables);

        List<CategoricalSummary> out = new ArrayList<>();
        for (String var : variables) {
            List<String> raw = colData.get(var);
            Map<String, Long> freq = new LinkedHashMap<>();
            long missing = 0;
            for (String s : raw) {
                if (s == null || s.trim().isEmpty() || s.trim().equalsIgnoreCase("na")) {
                    missing++;
                } else {
                    freq.merge(s.trim(), 1L, Long::sum);
                }
            }
            if (freq.isEmpty()) continue;
            long totalNonMissing = raw.size() - missing;
            List<CategoryCount> top = freq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(MAX_CATEGORIES)
                    .map(e -> new CategoryCount(e.getKey(), e.getValue(),
                            totalNonMissing > 0 ? Math.round(e.getValue() * 1000.0 / totalNonMissing) / 10.0 : 0.0))
                    .collect(Collectors.toList());
            out.add(new CategoricalSummary(var, freq.size(), missing, top));
        }
        return out;
    }

    public List<TimeSeriesPoint> computeTimeSeries(InputStream inputStream, String timeVar, String valueVar, int aggregation) throws Exception {
        Map<String, List<String>> colData = parseColumns(inputStream, Arrays.asList(timeVar, valueVar));
        List<String> times = colData.get(timeVar);
        List<String> values = colData.get(valueVar);

        // Group numeric values by a coarse time bucket (hour if timestamps parse, else raw label).
        Map<String, DescriptiveStatistics> buckets = new TreeMap<>();
        int n = Math.min(times.size(), values.size());
        for (int i = 0; i < n; i++) {
            String t = times.get(i);
            if (t == null || t.trim().isEmpty()) continue;
            String bucket = coarseTimeBucket(t.trim());
            if (bucket == null) continue;
            try {
                buckets.computeIfAbsent(bucket, k -> new DescriptiveStatistics()).addValue(Double.parseDouble(values.get(i).trim()));
            } catch (Exception ignored) {}
        }

        List<TimeSeriesPoint> points = new ArrayList<>();
        for (Map.Entry<String, DescriptiveStatistics> e : buckets.entrySet()) {
            if (e.getValue().getN() == 0) continue;
            double v = (aggregation == 1) ? e.getValue().getPercentile(50)
                    : (aggregation == 2) ? e.getValue().getMin()
                    : (aggregation == 3) ? e.getValue().getMax()
                    : e.getValue().getMean();
            points.add(new TimeSeriesPoint(e.getKey(), round2(v)));
        }
        if (points.size() > MAX_TIMESERIES_POINTS) {
            List<TimeSeriesPoint> condensed = new ArrayList<>();
            double step = (double) points.size() / MAX_TIMESERIES_POINTS;
            for (int i = 0; i < MAX_TIMESERIES_POINTS; i++) {
                condensed.add(points.get((int) (i * step)));
            }
            return condensed;
        }
        return points;
    }

    /**
     * Buckets a timestamp-ish string to hour resolution when it looks like an ISO datetime,
     * otherwise returns the raw string (so ordered category labels still chart).
     * Returns null for blank input.
     */
    private String coarseTimeBucket(String raw) {
        if (raw.isEmpty()) return null;
        if (raw.length() >= 13 && raw.charAt(4) == '-' && raw.charAt(7) == '-') {
            return raw.substring(0, Math.min(13, raw.length())); // date + hour when present
        }
        return raw;
    }

    private double round2(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Per-group distribution of a numeric variable measured on a SHARED set of bin
     * edges, so the resulting density curves are directly comparable between groups.
     */
    public static class GroupDistribution {
        private final String groupName;
        private final int count;
        private final double mean;
        private final double stdDev;
        private final double median;
        private final List<Double> binStarts;
        private final List<Double> binEnds;
        private final List<Long> counts;
        private final List<Double> proportions;

        public GroupDistribution(String groupName, int count, double mean, double stdDev, double median,
                                List<Double> binStarts, List<Double> binEnds, List<Long> counts, List<Double> proportions) {
            this.groupName = groupName;
            this.count = count;
            this.mean = mean;
            this.stdDev = stdDev;
            this.median = median;
            this.binStarts = binStarts;
            this.binEnds = binEnds;
            this.counts = counts;
            this.proportions = proportions;
        }

        public String getGroupName() { return groupName; }
        public int getCount() { return count; }
        public double getMean() { return mean; }
        public double getStdDev() { return stdDev; }
        public double getMedian() { return median; }
        public List<Double> getBinStarts() { return binStarts; }
        public List<Double> getBinEnds() { return binEnds; }
        public List<Long> getCounts() { return counts; }
        public List<Double> getProportions() { return proportions; }
    }

    /**
     * Distribution of a numeric variable split by a categorical variable, using
     * shared bins across all groups (comparable violin / density overlays).
     */
    public List<GroupDistribution> computeGroupDistributions(InputStream inputStream, String numVar, String catVar) throws Exception {
        Map<String, List<String>> colData = parseColumns(inputStream, Arrays.asList(numVar, catVar));
        List<String> numRaw = colData.get(numVar);
        List<String> catRaw = colData.get(catVar);

        Map<String, List<Double>> groups = new LinkedHashMap<>();
        int n = Math.min(numRaw.size(), catRaw.size());
        for (int i = 0; i < n; i++) {
            String sNum = numRaw.get(i);
            String sCat = catRaw.get(i);
            if (sNum == null || sCat == null) continue;
            if (sNum.trim().isEmpty() || sCat.trim().isEmpty()
                    || sNum.trim().equalsIgnoreCase("na") || sCat.trim().equalsIgnoreCase("na")) continue;
            try {
                groups.computeIfAbsent(sCat.trim(), k -> new ArrayList<>())
                        .add(Double.parseDouble(sNum.trim()));
            } catch (Exception ignored) {}
        }

        if (groups.isEmpty()) return Collections.emptyList();

        // Shared bin edges: min/max across all groups so the curves are comparable.
        double globalMin = Double.MAX_VALUE;
        double globalMax = -Double.MAX_VALUE;
        for (List<Double> vals : groups.values()) {
            for (double v : vals) {
                globalMin = Math.min(globalMin, v);
                globalMax = Math.max(globalMax, v);
            }
        }
        int bins = (globalMax > globalMin) ? MAX_GROUP_BINS : 1;
        double width = (globalMax > globalMin) ? (globalMax - globalMin) / bins : 1.0;

        List<Double> starts = new ArrayList<>();
        List<Double> ends = new ArrayList<>();
        for (int b = 0; b < bins; b++) {
            starts.add(round2(globalMin + b * width));
            ends.add(round2(globalMin + (b + 1) * width));
        }

        List<GroupDistribution> out = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : groups.entrySet()) {
            List<Double> vals = entry.getValue();
            if (vals.isEmpty()) continue;
            long[] binCounts = new long[bins];
            for (double v : vals) {
                int b = (globalMax > globalMin) ? Math.min(bins - 1, (int) ((v - globalMin) / width)) : 0;
                binCounts[Math.max(0, b)]++;
            }
            DescriptiveStatistics stats = new DescriptiveStatistics();
            vals.forEach(stats::addValue);

            List<Long> countList = new ArrayList<>();
            List<Double> proportions = new ArrayList<>();
            for (int b = 0; b < bins; b++) {
                countList.add(binCounts[b]);
                proportions.add(round4((double) binCounts[b] / vals.size()));
            }
            out.add(new GroupDistribution(
                    entry.getKey(),
                    vals.size(),
                    round2(stats.getMean()),
                    round2(stats.getStandardDeviation()),
                    round2(stats.getPercentile(50)),
                    starts, ends, countList, proportions));
        }
        out.sort(Comparator.comparing(GroupDistribution::getGroupName));
        return out;
    }

    /**
     * Row-level presence grid used to render a missing-value matrix heat-map.
     * Only the first {@code maxRows} rows are materialised so the payload stays bounded.
     */
    public static class MissingnessMatrix {
        private final List<String> variables;
        private final int[] rowIndexes;
        private final boolean[][] present;
        private final long totalRows;
        private final boolean truncated;

        public MissingnessMatrix(List<String> variables, int[] rowIndexes, boolean[][] present, long totalRows, boolean truncated) {
            this.variables = variables;
            this.rowIndexes = rowIndexes;
            this.present = present;
            this.totalRows = totalRows;
            this.truncated = truncated;
        }

        public List<String> getVariables() { return variables; }
        public int[] getRowIndexes() { return rowIndexes; }
        public boolean[][] getPresent() { return present; }
        public long getTotalRows() { return totalRows; }
        public boolean isTruncated() { return truncated; }
    }

    public MissingnessMatrix computeMissingnessMatrix(InputStream inputStream, List<DatasetColumn> columns, int maxRows) throws Exception {
        List<String> variables = columns.stream()
                .filter(c -> !"IDENTIFIER".equals(c.getDataType()))
                .map(DatasetColumn::getColumnName)
                .collect(Collectors.toList());
        int limit = Math.max(1, Math.min(maxRows, MAX_MISSINGNESS_MATRIX_ROWS));

        List<int[]> idx = new ArrayList<>();
        List<boolean[]> present = new ArrayList<>();
        long totalRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(false)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                long rowNumber = totalRows;
                totalRows++;
                if (idx.size() >= limit) continue;
                boolean[] row = new boolean[variables.size()];
                for (int c = 0; c < variables.size(); c++) {
                    String name = variables.get(c);
                    String value = record.isMapped(name) ? record.get(name) : null;
                    row[c] = !isMissingValue(value);
                }
                idx.add(new int[]{(int) rowNumber});
                present.add(row);
            }
        }

        int[] rowIndexes = new int[idx.size()];
        for (int i = 0; i < idx.size(); i++) rowIndexes[i] = idx.get(i)[0];
        boolean[][] grid = new boolean[present.size()][];
        for (int i = 0; i < present.size(); i++) grid[i] = present.get(i);

        return new MissingnessMatrix(variables, rowIndexes, grid, totalRows, totalRows > limit);
    }

    private boolean isMissingValue(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        return t.equalsIgnoreCase("na") || t.equalsIgnoreCase("null") || t.equalsIgnoreCase("nan")
                || t.equalsIgnoreCase("n/a") || t.equalsIgnoreCase("missing");
    }

    private double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 10000.0) / 10000.0;
    }

    private Map<String, List<String>> parseColumns(InputStream inputStream, List<String> targetColumns) throws Exception {
        Map<String, List<String>> result = new HashMap<>();
        targetColumns.forEach(c -> result.put(c, new ArrayList<>()));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(false)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                for (String col : targetColumns) {
                    if (record.isMapped(col)) {
                        result.get(col).add(record.get(col));
                    }
                }
            }
        }

        return result;
    }
}
