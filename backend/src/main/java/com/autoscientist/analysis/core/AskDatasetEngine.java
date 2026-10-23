package com.autoscientist.analysis.core;

import com.autoscientist.dto.ask.AskDatasetResponseDto;
import com.autoscientist.dto.ask.ChartDto;
import com.autoscientist.entity.*;
import com.autoscientist.util.ColumnSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AskDatasetEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CHART_POINTS = 30;
    private static final Pattern TOP_N = Pattern.compile("(?:top|first|highest|largest|bottom|lowest)\\s+(\\d+)");
    private static final Pattern GROUP_BY = Pattern.compile("(?:by|per|across|for each|grouped by)\\s+([a-z0-9_ -]{2,48})");
    private static final Pattern FILTER = Pattern.compile("where\\s+([a-z0-9_ -]{2,48}?)\\s+(?:=|is|equals|is equal to)\\s+([a-z0-9_. -]{1,40})");

    /** Parsed intent + parameters extracted from the natural-language question. */
    public static class ParsedQuery {
        public String intent;
        public List<String> mentioned = new ArrayList<>();
        public String aggregation = "mean";
        public String groupBy;
        public String filterColumn;
        public String filterValue;
        public int topN = 10;
        public String clarification;
        public boolean timeSeries;
    }

    public ParsedQuery parse(String query, List<DatasetColumn> activeColumns) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        // Normalized copy so "exam_score" matches a query written as "exam score"
        // (and vice versa) without weakening word-boundary matching.
        String qNorm = normalize(query);
        ParsedQuery p = new ParsedQuery();

        for (DatasetColumn c : activeColumns) {
            if (mentions(qNorm, c.getColumnName())) {
                p.mentioned.add(c.getColumnName());
            }
        }

        if (q.contains("median")) p.aggregation = "median";
        else if (q.contains("sum") || q.contains("total")) p.aggregation = "sum";
        else if (q.contains("std") || q.contains("deviation") || q.contains("spread")) p.aggregation = "std";
        else if (q.contains("count") || q.contains("how many") || q.contains("number of")) p.aggregation = "count";
        else if (q.contains("max") || q.contains("highest") || q.contains("largest")) p.aggregation = "max";
        else if (q.contains("min") || q.contains("lowest") || q.contains("smallest")) p.aggregation = "min";

        Matcher top = TOP_N.matcher(q);
        if (top.find()) {
            p.topN = Math.max(1, Math.min(50, Integer.parseInt(top.group(1))));
        }

        Matcher g = GROUP_BY.matcher(qNorm);
        if (g.find()) {
            String candidate = g.group(1).trim();
            for (DatasetColumn c : activeColumns) {
                if (!ColumnSelection.isIdentifier(c) && !"NUMERIC".equals(c.getDataType())
                        && (candidate.equals(normalize(c.getColumnName())) || candidate.contains(normalize(c.getColumnName())))) {
                    p.groupBy = c.getColumnName();
                    break;
                }
            }
        }

        Matcher f = FILTER.matcher(qNorm);
        if (f.find()) {
            String candidate = f.group(1).trim();
            for (DatasetColumn c : activeColumns) {
                if (candidate.equals(normalize(c.getColumnName())) || candidate.contains(normalize(c.getColumnName()))) {
                    p.filterColumn = c.getColumnName();
                    p.filterValue = f.group(2).trim();
                    break;
                }
            }
        }

        p.timeSeries = q.contains("over time") || q.contains("trend") || q.contains("timeline")
                || q.contains("by month") || q.contains("by year") || q.contains("by date")
                || q.contains("time series") || q.contains("timeseries");

        boolean causal = q.contains("why ") || q.startsWith("why") || q.contains("cause") || q.contains("caused")
                || q.contains("causal") || q.contains("impact of") || q.contains("effect of")
                || q.contains("because of") || q.contains("what led to");
        boolean quality = q.contains("quality") || q.contains("missing") || q.contains("null")
                || q.contains("duplicate") || q.contains("issue") || q.contains("dirty") || q.contains("clean");
        boolean ml = q.contains("model") || q.contains("predict") || q.contains("automl")
                || q.contains("algorithm") || q.contains("f1") || q.contains("accuracy") || q.contains(" r2")
                || q.contains("trained") || q.contains("classifier") || q.contains("regression");
        boolean assoc = q.contains("correlat") || q.contains("associat") || q.contains("relationship")
                || q.contains("link") || q.contains("depend");
        boolean distribution = q.contains("distribution") || q.contains("frequency") || q.contains("breakdown")
                || q.contains("most common") || q.contains("proportion") || q.contains("percent")
                || q.contains("count") || q.contains("how many") || q.contains("unique values");

        if (causal) {
            p.intent = "CAUSAL_INFERENCE_UNSUPPORTED";
        } else if (quality) {
            p.intent = "DATA_QUALITY_ASSESSMENT";
        } else if (ml) {
            p.intent = "MACHINE_LEARNING_PERFORMANCE";
        } else if (assoc) {
            p.intent = "STATISTICAL_ASSOCIATIONS";
        } else if (p.timeSeries) {
            p.intent = "TIME_TREND";
        } else if (distribution) {
            p.intent = "CATEGORICAL_DISTRIBUTION";
        } else if (p.groupBy != null) {
            p.intent = "GROUP_COMPARISON";
        } else if (q.contains("mean") || q.contains("average") || q.contains("avg") || q.contains("median")
                || q.contains("typical")) {
            p.intent = "NUMERIC_CENTRAL_TENDENCY";
        } else if (q.contains("max") || q.contains("min") || q.contains("highest") || q.contains("lowest")
                || q.contains("range") || q.contains("spread") || q.contains("outlier")) {
            p.intent = "EXTREMA_AND_SPREAD";
        } else if (q.contains("describe") || q.contains("summary") || q.contains("summarize")
                || q.contains("statistic") || q.contains("tell me about")) {
            p.intent = "DESCRIBE_COLUMN";
        } else if (q.contains("dataset") || q.contains("data") || q.contains("overview")
                || q.contains("describe") || q.contains("what is this")) {
            p.intent = "DATASET_OVERVIEW";
        } else if (!p.mentioned.isEmpty()) {
            p.intent = "DESCRIBE_COLUMN";
        } else {
            p.intent = "UNSUPPORTED";
        }

        if (("NUMERIC_CENTRAL_TENDENCY".equals(p.intent) || "EXTREMA_AND_SPREAD".equals(p.intent)
                || "DESCRIBE_COLUMN".equals(p.intent)) && p.mentioned.size() > 1) {
            p.clarification = "You mentioned more than one variable (" + String.join(", ", p.mentioned)
                    + "). Please ask about one variable at a time, or add e.g. \"by <group>\" for a comparison.";
        }
        if (("NUMERIC_CENTRAL_TENDENCY".equals(p.intent) || "EXTREMA_AND_SPREAD".equals(p.intent))
                && p.mentioned.isEmpty()) {
            p.clarification = "Please name the variable you want to summarize.";
        }
        return p;
    }

    /** Backwards-compatible entry point: answers without live row access (metadata only). */
    public AskDatasetResponseDto answerQuery(String query, Dataset dataset,
                                             DataQualityReport qualityReport,
                                             List<Association> associations,
                                             List<ModelRun> modelRuns) {
        return answerQuery(query, dataset, qualityReport, associations, modelRuns, Collections.emptyMap());
    }

    public AskDatasetResponseDto answerQuery(String query, Dataset dataset,
                                             DataQualityReport qualityReport,
                                             List<Association> associations,
                                             List<ModelRun> modelRuns,
                                             Map<String, List<String>> rawColumns) {
        List<DatasetColumn> activeColumns = ColumnSelection.active(dataset);
        ParsedQuery parsed = parse(query, activeColumns);
        Map<String, DatasetColumn> byName = activeColumns.stream()
                .collect(Collectors.toMap(c -> c.getColumnName().toLowerCase(Locale.ROOT), c -> c, (a, b) -> a));

        Map<String, Object> evidence = new LinkedHashMap<>();
        List<String> citations = new ArrayList<>();
        double confidence = 0.95;
        String answer;
        ChartDto chart = null;

        switch (parsed.intent) {
            case "CAUSAL_INFERENCE_UNSUPPORTED" -> {
                confidence = 0.9;
                answer = "### Causal questions are not supported\n\n"
                        + "This platform analyses observational data. It can establish **statistical association** "
                        + "(correlation, group differences) but cannot identify causal mechanisms without an "
                        + "experimental design or a formal causal model.\n\n"
                        + "Supported alternatives: ask for correlations/associations, group comparisons, "
                        + "distributions, or model feature importance. Those measure association, not causation.";
                evidence.put("supported", false);
                evidence.put("reason", "OBSERVATIONAL_DATA_ONLY");
            }
            case "DATA_QUALITY_ASSESSMENT" -> {
                if (qualityReport != null) {
                    answer = String.format("### Data Quality Integrity Summary\n" +
                                    "- **Overall Quality Score**: **%.1f / 100**\n" +
                                    "- **Missing Values Score**: `%.1f / 100`\n" +
                                    "- **Duplicate Rows Score**: `%.1f / 100`\n" +
                                    "- **Total Identified Issues**: `%d`\n\n" +
                                    "The dataset exhibits an overall rating of **%s**.",
                            qualityReport.getOverallScore(), qualityReport.getMissingValueScore(),
                            qualityReport.getDuplicateScore(), qualityReport.getTotalIssues(),
                            qualityReport.getOverallScore() >= 80 ? "EXCELLENT"
                                    : qualityReport.getOverallScore() >= 60 ? "MODERATE" : "NEEDS_CLEANING");
                    evidence.put("overallScore", qualityReport.getOverallScore());
                    evidence.put("totalIssues", qualityReport.getTotalIssues());
                    citations.add("DataQualityReport for dataset '" + dataset.getName() + "' (persisted quality audit)");
                    chart = ChartDto.builder()
                            .type("bar")
                            .title("Quality subscores (/100)")
                            .labels(List.of("Missing values", "Duplicates", "Outliers", "Consistency"))
                            .values(Arrays.asList(
                                    safe(qualityReport.getMissingValueScore()),
                                    safe(qualityReport.getDuplicateScore()),
                                    safe(qualityReport.getOutlierScore()),
                                    safe(qualityReport.getConsistencyScore())))
                            .xLabel("Dimension").yLabel("Score")
                            .build();
                } else {
                    confidence = 0.8;
                    answer = "No data quality audit has been executed on this dataset yet. Open the dataset page to run profiling.";
                }
            }
            case "STATISTICAL_ASSOCIATIONS" -> {
                if (associations != null && !associations.isEmpty()) {
                    List<Association> topAssoc = associations.stream()
                            .filter(a -> parsed.mentioned.isEmpty() || parsed.mentioned.contains(a.getVar1())
                                    || parsed.mentioned.contains(a.getVar2()))
                            .limit(Math.max(4, parsed.topN))
                            .collect(Collectors.toList());
                    StringBuilder sb = new StringBuilder("### Top Empirical Associations\n\n");
                    sb.append("| Variable 1 | Variable 2 | Test | Statistic | p-value | Effect size |\n");
                    sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
                    for (Association a : topAssoc) {
                        sb.append(String.format("| `%s` | `%s` | %s | %.3f | %.4e | %.3f |\n",
                                a.getVar1(), a.getVar2(), a.getTestType(), a.getStatistic(), a.getPValue(), a.getEffectSize()));
                        evidence.put(a.getVar1() + "_vs_" + a.getVar2(), a.getEffectSize());
                        citations.add("Association test " + a.getTestType() + " on '" + a.getVar1()
                                + "' vs '" + a.getVar2() + "' (N=" + a.getSampleSize() + ")");
                    }
                    sb.append("\n> Command: associations indicate correlation, not causality.\n");
                    answer = sb.toString();
                    chart = ChartDto.builder()
                            .type("bar")
                            .title("Association strength (|effect size|)")
                            .labels(topAssoc.stream().map(a -> a.getVar1() + " vs " + a.getVar2()).toList())
                            .values(topAssoc.stream().map(a -> Math.abs(a.getEffectSize())).toList())
                            .xLabel("Variable pair").yLabel("|effect size|")
                            .build();
                } else {
                    confidence = 0.8;
                    answer = "No bivariate associations have been computed yet. Run 'Find Associations' to calculate empirical statistical tests.";
                }
            }
            case "MACHINE_LEARNING_PERFORMANCE" -> {
                if (modelRuns != null && !modelRuns.isEmpty()) {
                    ModelRun best = modelRuns.stream().filter(ModelRun::getIsBestModel).findFirst().orElse(modelRuns.get(0));
                    Map<String, Double> valMetrics = parseMetrics(best.getValMetricsJson());
                    Map<String, Double> testMetrics = parseMetrics(best.getTestMetricsJson());
                    boolean classification = valMetrics.containsKey("f1");
                    String primary = classification ? "f1" : "r2";
                    answer = String.format("### Best Predictive Model: %s\n\n" +
                                    "- **Algorithm**: `%s`\n" +
                                    "- **Validation %s**: `%.4f`\n" +
                                    "- **Test metrics**: `%s`\n" +
                                    "- **Sample sizes**: train=%d, validation=%d, test=%d\n" +
                                    "- **Random seed**: `%s`\n\n" +
                                    "This model was evaluated on a held-out split; the reported test metrics are the honest estimate of generalisation.",
                            best.getModelName(), best.getAlgorithm(),
                            classification ? "F1" : "R²", valMetrics.getOrDefault(primary, 0.0),
                            best.getTestMetricsJson(), best.getTrainRows(), best.getValRows(), best.getTestRows(),
                            best.getRandomSeed());
                    evidence.put("bestModel", best.getModelName());
                    evidence.put("validationMetrics", valMetrics);
                    citations.add("ModelRun #" + best.getId() + " ('" + best.getModelName()
                            + "') persisted with validation and test metrics");
                    List<ModelRun> all = modelRuns.stream()
                            .filter(r -> parseMetrics(r.getValMetricsJson()).containsKey(primary))
                            .limit(MAX_CHART_POINTS).toList();
                    if (!all.isEmpty()) {
                        chart = ChartDto.builder()
                                .type("bar")
                                .title("Model leaderboard (validation " + (classification ? "F1" : "R²") + ")")
                                .labels(all.stream().map(ModelRun::getModelName).toList())
                                .values(all.stream().map(r -> parseMetrics(r.getValMetricsJson()).getOrDefault(primary, 0.0)).toList())
                                .xLabel("Model").yLabel(classification ? "F1" : "R²")
                                .build();
                    }
                } else {
                    confidence = 0.8;
                    answer = "No machine learning models have been trained on this dataset yet. Use the AutoML engine to benchmark candidate algorithms.";
                }
            }
            case "CATEGORICAL_DISTRIBUTION" -> {
                if (parsed.mentioned.isEmpty()) {
                    confidence = 0.65;
                    answer = "Which variable's distribution should I summarize? Mention a column name, "
                            + "for example: \"distribution of status\" or \"top categories of region\".";
                } else {
                    String var = parsed.mentioned.get(0);
                    DatasetColumn col = byName.get(var.toLowerCase(Locale.ROOT));
                    List<String> raw = rawColumns.get(var);
                    if (col == null) {
                        answer = "Unknown variable: " + var;
                        confidence = 0.5;
                    } else if ("NUMERIC".equals(col.getDataType()) && raw != null) {
                        List<Double> nums = parseDoubles(raw);
                        chart = histogram(var, nums);
                        answer = String.format("### Distribution of `%s`\n\n%d numeric observations, "
                                        + "min=%.3f, median=%.3f, max=%.3f. See the histogram for shape.",
                                var, nums.size(), percentile(nums, 0), percentile(nums, 50), percentile(nums, 100));
                        evidence.put("n", nums.size());
                        citations.add("Live values of column '" + var + "' from the stored dataset");
                    } else {
                        Map<String, Long> freq = raw != null ? frequency(raw) : Collections.emptyMap();
                        if (freq.isEmpty()) {
                            answer = "No non-missing values were available for `" + var + "`.";
                            confidence = 0.6;
                        } else {
                            long total = freq.values().stream().mapToLong(Long::longValue).sum();
                            StringBuilder sb = new StringBuilder("### Distribution of `" + var + "`\n\n");
                            sb.append("| Category | Count | Share |\n| :--- | :--- | :--- |\n");
                            freq.entrySet().stream().limit(parsed.topN).forEach(e -> sb.append(String.format(
                                    "| `%s` | %d | %.1f%% |\n", e.getKey(), e.getValue(),
                                    total > 0 ? e.getValue() * 100.0 / total : 0.0)));
                            answer = sb.toString();
                            evidence.put("uniqueCategories", freq.size());
                            citations.add("Frequency counts of column '" + var + "' (N=" + total + " non-missing)");
                            chart = ChartDto.builder()
                                    .type("bar")
                                    .title("Distribution of " + var)
                                    .labels(freq.keySet().stream().limit(parsed.topN).toList())
                                    .values(freq.values().stream().limit(parsed.topN).map(Long::doubleValue).toList())
                                    .xLabel(var).yLabel("Count")
                                    .build();
                        }
                    }
                }
            }
            case "GROUP_COMPARISON" -> {
                if (parsed.groupBy == null) {
                    confidence = 0.6;
                    answer = "Which grouping variable should I use? For example: \"average revenue by region\".";
                } else if (parsed.mentioned.isEmpty()) {
                    confidence = 0.6;
                    answer = "Which numeric variable should I compare across '" + parsed.groupBy + "'?";
                } else {
                    String numVar = parsed.mentioned.stream()
                            .filter(n -> byName.containsKey(n.toLowerCase(Locale.ROOT))
                                    && "NUMERIC".equals(byName.get(n.toLowerCase(Locale.ROOT)).getDataType()))
                            .findFirst().orElse(null);
                    List<String> numRaw = numVar != null ? rawColumns.get(numVar) : null;
                    List<String> catRaw = rawColumns.get(parsed.groupBy);
                    if (numVar == null) {
                        confidence = 0.6;
                        answer = "The variables you mentioned are not numeric, so a numeric group comparison cannot be computed.";
                    } else if (numRaw == null || catRaw == null) {
                        answer = "Group comparison requires the raw rows; they could not be loaded for this dataset.";
                        confidence = 0.5;
                    } else {
                        Map<String, List<Double>> groups = groupValues(numRaw, catRaw);
                        String agg = parsed.aggregation;
                        StringBuilder sb = new StringBuilder("### " + capitalize(agg) + " of `" + numVar
                                + "` by `" + parsed.groupBy + "`\n\n");
                        sb.append("| ").append(parsed.groupBy).append(" | N | ").append(capitalize(agg)).append(" |\n");
                        sb.append("| :--- | :--- | :--- |\n");
                        Map<String, Double> values = new LinkedHashMap<>();
                        for (Map.Entry<String, List<Double>> e : groups.entrySet()) {
                            double v = aggregate(e.getValue(), agg);
                            values.put(e.getKey(), v);
                            sb.append(String.format("| `%s` | %d | %.3f |\n", e.getKey(), e.getValue().size(), v));
                        }
                        answer = sb.toString();
                        evidence.put("groups", values.size());
                        citations.add("Live grouped aggregation of '" + numVar + "' by '" + parsed.groupBy + "'");
                        chart = ChartDto.builder()
                                .type("bar")
                                .title(capitalize(agg) + " of " + numVar + " by " + parsed.groupBy)
                                .labels(new ArrayList<>(values.keySet()))
                                .values(new ArrayList<>(values.values()))
                                .xLabel(parsed.groupBy).yLabel(capitalize(agg))
                                .build();
                    }
                }
            }
            case "TIME_TREND" -> {
                List<String> timeCandidates = activeColumns.stream()
                        .filter(c -> "DATE_TIME".equals(c.getDataType()) || normalize(c.getColumnName()).contains("date")
                                || normalize(c.getColumnName()).contains("time"))
                        .map(DatasetColumn::getColumnName).toList();
                String timeVar = !parsed.mentioned.isEmpty()
                        ? parsed.mentioned.stream().filter(timeCandidates::contains).findFirst().orElse(
                                timeCandidates.isEmpty() ? null : timeCandidates.get(0))
                        : (timeCandidates.isEmpty() ? null : timeCandidates.get(0));
                String valueVar = parsed.mentioned.stream()
                        .filter(n -> byName.containsKey(n.toLowerCase(Locale.ROOT))
                                && "NUMERIC".equals(byName.get(n.toLowerCase(Locale.ROOT)).getDataType()))
                        .findFirst().orElse(null);
                if (timeVar == null || valueVar == null) {
                    confidence = 0.6;
                    answer = "A time trend needs a time variable (DATE_TIME or date/time-named) and a numeric value "
                            + "variable. Mention both, e.g. \"sales over time\".";
                } else {
                    List<String> times = rawColumns.get(timeVar);
                    List<String> vals = rawColumns.get(valueVar);
                    if (times == null || vals == null) {
                        answer = "Time-series data could not be loaded for this dataset.";
                        confidence = 0.5;
                    } else {
                        Map<String, List<Double>> buckets = new TreeMap<>();
                        int n = Math.min(times.size(), vals.size());
                        for (int i = 0; i < n; i++) {
                            String t = times.get(i);
                            if (t == null || t.isBlank()) continue;
                            try {
                                buckets.computeIfAbsent(t.trim().length() >= 13 ? t.trim().substring(0, 13) : t.trim(),
                                        k -> new ArrayList<>()).add(Double.parseDouble(vals.get(i).trim()));
                            } catch (Exception ignored) {}
                        }
                        if (buckets.isEmpty()) {
                            confidence = 0.6;
                            answer = "No numeric values could be aligned to the time variable '" + timeVar + "'.";
                        } else {
                            List<String> labels = buckets.keySet().stream().limit(MAX_CHART_POINTS * 4).toList();
                            answer = String.format("### Trend of `%s` over `%s`\n\n%d time buckets. "
                                            + "Values are the mean of each bucket; the line chart shows the trajectory.",
                                    valueVar, timeVar, buckets.size());
                            citations.add("Live time bucketing of '" + valueVar + "' by '" + timeVar + "'");
                            chart = ChartDto.builder()
                                    .type("line")
                                    .title(valueVar + " over " + timeVar)
                                    .labels(labels)
                                    .values(labels.stream().map(l -> average(buckets.get(l))).toList())
                                    .xLabel(timeVar).yLabel(valueVar)
                                    .build();
                        }
                    }
                }
            }
            case "NUMERIC_CENTRAL_TENDENCY", "EXTREMA_AND_SPREAD", "DESCRIBE_COLUMN" -> {
                if (parsed.clarification != null) {
                    confidence = 0.6;
                    answer = "### Clarification needed\n\n" + parsed.clarification;
                } else if (parsed.mentioned.isEmpty()) {
                    confidence = 0.65;
                    answer = "Please name the variable you want to summarize.";
                } else {
                    String var = parsed.mentioned.get(0);
                    DatasetColumn col = byName.get(var.toLowerCase(Locale.ROOT));
                    if (col == null) {
                        confidence = 0.5;
                        answer = "Unknown variable: " + var;
                    } else {
                        List<String> raw = rawColumns.get(var);
                        List<Double> nums = raw != null ? parseDoubles(raw) : Collections.emptyList();
                        if (!"NUMERIC".equals(col.getDataType())) {
                            Map<String, Long> freq = raw != null ? frequency(raw) : Collections.emptyMap();
                            answer = String.format("Column `%s` is typed as `%s` (non-numeric), so an arithmetic "
                                            + "mean is undefined. It contains %d unique categories across %d non-missing rows.",
                                    col.getColumnName(), col.getDataType(), col.getUniqueCount(),
                                    raw != null ? raw.size() : dataset.getRowCount() - col.getMissingCount());
                            citations.add("Column metadata for '" + col.getColumnName() + "' (" + col.getDataType() + ")");
                            if (!freq.isEmpty()) {
                                chart = ChartDto.builder().type("bar").title("Top categories of " + var)
                                        .labels(freq.keySet().stream().limit(parsed.topN).toList())
                                        .values(freq.values().stream().limit(parsed.topN).map(Long::doubleValue).toList())
                                        .xLabel(var).yLabel("Count").build();
                            }
                        } else if (nums.isEmpty() && col.getMeanVal() == null) {
                            confidence = 0.6;
                            answer = "No numeric values were available for `" + var + "`.";
                        } else if (nums.isEmpty()) {
                            answer = String.format("### Summary of `%s`\n\n- Mean: `%.3f`\n- Median: `%.3f`\n"
                                            + "- Std dev: `%.3f`\n- Range: `[%.3f, %.3f]`\n\nBased on the persisted profile "
                                            + "(%d non-missing observations).",
                                    var, col.getMeanVal(), col.getMedianVal(), col.getStdDev(),
                                    col.getMinVal(), col.getMaxVal(), dataset.getRowCount() - col.getMissingCount());
                            evidence.put("mean", col.getMeanVal());
                            evidence.put("median", col.getMedianVal());
                            citations.add("Persisted profile values for '" + var + "'");
                        } else {
                            double mean = average(nums);
                            double median = percentile(nums, 50);
                            double std = stdDev(nums, mean);
                            double min = percentile(nums, 0);
                            double max = percentile(nums, 100);
                            double q1 = percentile(nums, 25);
                            double q3 = percentile(nums, 75);
                            answer = String.format("### Summary of `%s`\n\n" +
                                            "- **N (non-missing)**: %d\n" +
                                            "- **Mean**: `%.3f`\n" +
                                            "- **Median**: `%.3f`\n" +
                                            "- **Std dev**: `%.3f`\n" +
                                            "- **Range**: `[%.3f, %.3f]`\n" +
                                            "- **IQR**: `[%.3f, %.3f]`\n\n" +
                                            "Computed live from the stored dataset rows.",
                                    var, nums.size(), mean, median, std, min, max, q1, q3);
                            evidence.put("mean", round(mean));
                            evidence.put("median", round(median));
                            evidence.put("stdDev", round(std));
                            evidence.put("n", nums.size());
                            citations.add("Live computation over column '" + var + "' (N=" + nums.size() + ")");
                            chart = histogram(var, nums);
                        }
                    }
                }
            }
            default -> {
                long numericCount = activeColumns.stream().filter(c -> "NUMERIC".equalsIgnoreCase(c.getDataType())).count();
                long catCount = activeColumns.stream().filter(c -> "CATEGORICAL".equalsIgnoreCase(c.getDataType())).count();
                if ("UNSUPPORTED".equals(parsed.intent)) {
                    confidence = 0.85;
                    answer = "### I can't answer that from this dataset\n\n"
                            + "I only execute analyses that are backed by the stored data: descriptive statistics, "
                            + "distributions, group comparisons, correlations/associations, data quality, time trends, "
                            + "and model evaluation. I will not invent results for questions outside those operations.\n\n"
                            + "Try: \"average age\", \"distribution of region\", \"age by sex\", "
                            + "\"missing values\", or \"best model\".";
                    evidence.put("supported", false);
                } else {
                    answer = String.format("### Dataset Overview: `%s`\n\n" +
                                    "- **Total Observations (Rows)**: `%d`\n" +
                                    "- **Total Features (Columns)**: `%d` (%d numeric, %d categorical)\n" +
                                    "- **Detected Problem Suitability**: `%s`\n" +
                                    "- **Original File**: `%s` (%.1f KB)\n\n" +
                                    "You can ask about specific column statistics, distributions, group comparisons, "
                                    + "data quality, associations, time trends, or model performance.",
                            dataset.getName(), dataset.getRowCount(), dataset.getColumnCount(),
                            numericCount, catCount, dataset.getDatasetType(),
                            dataset.getOriginalFilename(), dataset.getFileSize() / 1024.0);
                    evidence.put("rowCount", dataset.getRowCount());
                    evidence.put("columnCount", dataset.getColumnCount());
                    evidence.put("datasetType", dataset.getDatasetType());
                    citations.add("Dataset metadata for '" + dataset.getName() + "'");
                    chart = ChartDto.builder()
                            .type("bar")
                            .title("Variable types")
                            .labels(List.of("Numeric", "Categorical"))
                            .values(List.of((double) numericCount, (double) catCount))
                            .xLabel("Type").yLabel("Columns")
                            .build();
                }
            }
        }

        return AskDatasetResponseDto.builder()
                .query(query)
                .detectedIntent(parsed.intent)
                .relevantColumns(parsed.mentioned)
                .answerMarkdown(answer)
                .empiricalEvidence(evidence)
                .confidence(confidence)
                .chart(chart)
                .citations(citations)
                .build();
    }

    // --- parsing helpers ---

    private boolean mentions(String query, String columnName) {
        String normalized = normalize(columnName);
        if (normalized.isBlank()) return false;
        if (normalized.contains(" ")) {
            return query.contains(normalized);
        }
        // Short names must match on word boundaries so "id" does not match "did".
        return Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(normalized) + "(?![a-z0-9_])")
                .matcher(query).find();
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim();
    }

    // --- data helpers ---

    private List<Double> parseDoubles(List<String> raw) {
        if (raw == null) return Collections.emptyList();
        List<Double> out = new ArrayList<>();
        for (String s : raw) {
            try { out.add(Double.parseDouble(s.trim())); } catch (Exception ignored) {}
        }
        return out;
    }

    private Map<String, Long> frequency(List<String> raw) {
        Map<String, Long> freq = new LinkedHashMap<>();
        for (String s : raw) {
            if (s == null || s.isBlank() || s.trim().equalsIgnoreCase("na")) continue;
            freq.merge(s.trim(), 1L, Long::sum);
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, List<Double>> groupValues(List<String> numRaw, List<String> catRaw) {
        Map<String, List<Double>> groups = new LinkedHashMap<>();
        int n = Math.min(numRaw.size(), catRaw.size());
        for (int i = 0; i < n; i++) {
            String cat = catRaw.get(i);
            if (cat == null || cat.isBlank()) continue;
            try {
                groups.computeIfAbsent(cat.trim(), k -> new ArrayList<>())
                        .add(Double.parseDouble(numRaw.get(i).trim()));
            } catch (Exception ignored) {}
        }
        return groups;
    }

    private double aggregate(List<Double> values, String agg) {
        if (values.isEmpty()) return 0.0;
        return switch (agg) {
            case "median" -> percentile(values, 50);
            case "sum" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "std" -> stdDev(values, average(values));
            case "count" -> values.size();
            case "max" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            case "min" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            default -> average(values);
        };
    }

    private ChartDto histogram(String var, List<Double> nums) {
        if (nums.isEmpty()) return null;
        int bins = Math.max(5, Math.min(20, (int) Math.ceil(Math.sqrt(nums.size()))));
        double min = percentile(nums, 0), max = percentile(nums, 100);
        double width = max > min ? (max - min) / bins : 1.0;
        long[] counts = new long[bins];
        for (double v : nums) {
            int b = max > min ? Math.min(bins - 1, (int) ((v - min) / width)) : 0;
            counts[Math.max(0, b)]++;
        }
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int b = 0; b < bins; b++) {
            labels.add(String.format("%.1f–%.1f", min + b * width, min + (b + 1) * width));
            values.add((double) counts[b]);
        }
        return ChartDto.builder().type("histogram").title("Distribution of " + var)
                .labels(labels).values(values).xLabel(var).yLabel("Count").build();
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double stdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0.0;
        double ss = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return Math.sqrt(ss / (values.size() - 1));
    }

    private double percentile(List<Double> values, double p) {
        if (values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        if (sorted.size() == 1) return sorted.get(0);
        double rank = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        double w = rank - lo;
        return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
    }

    private Map<String, Double> parseMetrics(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private double safe(Double v) {
        return v == null ? 0.0 : v;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
