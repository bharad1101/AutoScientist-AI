package com.autoscientist.service;

import com.autoscientist.dto.research.EvidenceGraphDto;
import com.autoscientist.entity.*;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.repository.*;
import com.autoscientist.util.ColumnSelection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvidenceGraphService {

    private final DatasetService datasetService;
    private final AssociationRepository associationRepository;
    private final ResearchQuestionRepository researchQuestionRepository;
    private final HypothesisRepository hypothesisRepository;
    private final StatisticalTestRepository statisticalTestRepository;
    private final FindingRepository findingRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentVersionRepository experimentVersionRepository;
    private final ModelRunRepository modelRunRepository;
    private final ReportRepository reportRepository;
    private final AnalysisRunRepository analysisRunRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public EvidenceGraphDto buildGraph(Long datasetId, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        List<EvidenceGraphDto.Node> nodes = new ArrayList<>();
        List<EvidenceGraphDto.Edge> edges = new ArrayList<>();

        String dsId = "dataset:" + dataset.getId();
        nodes.add(EvidenceGraphDto.Node.builder()
                .id(dsId).type("DATASET").label(dataset.getName())
                .detail(String.format("%d rows × %d columns · %s", dataset.getRowCount(), dataset.getColumnCount(),
                        dataset.getDatasetType()))
                .meta(meta("version", dataset.getVersion(), "sourceFormat", dataset.getSourceFormat(),
                        "rows", dataset.getRowCount(), "columns", dataset.getColumnCount()))
                .build());

        // Variables (analytic/included columns are marked; identifiers stay visible but flagged)
        Set<String> excluded = ColumnSelection.excludedNames(dataset);
        for (DatasetColumn col : dataset.getColumns()) {
            String colId = "column:" + col.getColumnName();
            boolean isId = "IDENTIFIER".equals(col.getDataType());
            nodes.add(EvidenceGraphDto.Node.builder()
                    .id(colId).type("VARIABLE").label(col.getColumnName())
                    .detail(col.getDataType() + (isId ? " · identifier (excluded from analysis)"
                            : excluded.contains(col.getColumnName()) ? " · user-excluded" : " · included"))
                    .meta(meta("dataType", col.getDataType(), "missingPct", col.getMissingPct(),
                            "uniqueCount", col.getUniqueCount(), "excluded", excluded.contains(col.getColumnName()),
                            "identifier", isId))
                    .build());
            edges.add(EvidenceGraphDto.Edge.builder().from(dsId).to(colId).type("CONTAINS").label("contains").build());
        }

        // Associations
        for (Association a : associationRepository.findByDatasetOrderByRankScoreDesc(dataset)) {
            String id = "association:" + a.getId();
            nodes.add(EvidenceGraphDto.Node.builder()
                    .id(id).type("ASSOCIATION").label(a.getVar1() + " ↔ " + a.getVar2())
                    .detail(String.format("%s · stat=%.3f · p=%.4e · effect=%.3f", a.getTestType(),
                            a.getStatistic(), a.getPValue(), a.getEffectSize()))
                    .meta(meta("testType", a.getTestType(), "statistic", a.getStatistic(),
                            "pValue", a.getPValue(), "effectSize", a.getEffectSize(),
                            "significant", a.getIsSignificant(), "sampleSize", a.getSampleSize()))
                    .build());
            edges.add(EvidenceGraphDto.Edge.builder().from(dsId).to(id).type("DERIVED_FROM").label("association").build());
            if (columnExists(nodes, a.getVar1()))
                edges.add(EvidenceGraphDto.Edge.builder().from(id).to("column:" + a.getVar1()).type("INVOLVES").label("var1").build());
            if (columnExists(nodes, a.getVar2()))
                edges.add(EvidenceGraphDto.Edge.builder().from(id).to("column:" + a.getVar2()).type("INVOLVES").label("var2").build());
        }

        // Research questions → hypotheses → tests → findings
        List<ResearchQuestion> questions = researchQuestionRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        for (ResearchQuestion q : questions) {
            String qId = "question:" + q.getId();
            nodes.add(EvidenceGraphDto.Node.builder()
                    .id(qId).type("QUESTION").label(truncate(q.getQuestionText(), 90))
                    .detail(q.getSuggestedTest() + " · target=" + q.getTargetVariable()
                            + " · predictor=" + q.getPredictorVariable())
                    .meta(meta("questionId", q.getId(), "target", q.getTargetVariable(),
                            "predictor", q.getPredictorVariable(), "suggestedTest", q.getSuggestedTest()))
                    .build());
            edges.add(EvidenceGraphDto.Edge.builder().from(dsId).to(qId).type("POSES").label("question").build());

            for (Hypothesis h : hypothesisRepository.findByResearchQuestionOrderByRankOrderAsc(q)) {
                String hId = "hypothesis:" + h.getId();
                nodes.add(EvidenceGraphDto.Node.builder()
                        .id(hId).type("HYPOTHESIS").label("H" + h.getRankOrder() + ": " + truncate(h.getAltHypothesis(), 80))
                        .detail("H0: " + truncate(h.getNullHypothesis(), 120))
                        .meta(meta("hypothesisId", h.getId(), "suggestedTest", h.getSuggestedTest()))
                        .build());
                edges.add(EvidenceGraphDto.Edge.builder().from(qId).to(hId).type("TESTS").label("hypothesis").build());

                for (StatisticalTest t : statisticalTestRepository.findByHypothesis(h)) {
                    String tId = "test:" + t.getId();
                    nodes.add(EvidenceGraphDto.Node.builder()
                            .id(tId).type("STAT_TEST").label(t.getTestType() + ": " + t.getVar1()
                                    + (t.getVar2() != null ? " vs " + t.getVar2() : ""))
                            .detail(String.format("stat=%.3f · p=%.4e · %s=%.3f · N=%s · %s",
                                    t.getStatistic(), t.getPValue(), t.getEffectSizeType(), t.getEffectSize(),
                                    t.getSampleSize(), Boolean.TRUE.equals(t.getIsStatisticallySignificant())
                                            ? "significant" : "not significant"))
                            .meta(meta("testId", t.getId(), "pValue", t.getPValue(), "effectSize", t.getEffectSize(),
                                    "sampleSize", t.getSampleSize(), "significant", t.getIsStatisticallySignificant(),
                                    "status", t.getStatus()))
                            .build());
                    edges.add(EvidenceGraphDto.Edge.builder().from(hId).to(tId).type("EVALUATED_BY").label("test").build());
                    if (t.getVar1() != null && columnExists(nodes, t.getVar1()))
                        edges.add(EvidenceGraphDto.Edge.builder().from(tId).to("column:" + t.getVar1()).type("MEASURES").label("var1").build());
                    if (t.getVar2() != null && columnExists(nodes, t.getVar2()))
                        edges.add(EvidenceGraphDto.Edge.builder().from(tId).to("column:" + t.getVar2()).type("MEASURES").label("var2").build());
                }
            }
        }

        for (Finding f : findingRepository.findByDatasetOrderByCreatedAtDesc(dataset)) {
            String fId = "finding:" + f.getId();
            nodes.add(EvidenceGraphDto.Node.builder()
                    .id(fId).type("FINDING").label(truncate(f.getTitle(), 100))
                    .detail("Confidence: " + f.getConfidenceLevel())
                    .meta(meta("findingId", f.getId(), "confidence", f.getConfidenceLevel(),
                            "hypothesisId", f.getHypothesis() != null ? f.getHypothesis().getId() : null,
                            "testId", f.getStatisticalTest() != null ? f.getStatisticalTest().getId() : null))
                    .build());
            edges.add(EvidenceGraphDto.Edge.builder().from(dsId).to(fId).type("YIELDS").label("finding").build());
            if (f.getStatisticalTest() != null && nodeExists(nodes, "test:" + f.getStatisticalTest().getId()))
                edges.add(EvidenceGraphDto.Edge.builder().from("test:" + f.getStatisticalTest().getId())
                        .to(fId).type("SUPPORTS").label("evidence").build());
            if (f.getHypothesis() != null && nodeExists(nodes, "hypothesis:" + f.getHypothesis().getId()))
                edges.add(EvidenceGraphDto.Edge.builder().from("hypothesis:" + f.getHypothesis().getId())
                        .to(fId).type("RESOLVED_AS").label("finding").build());
        }

        // Experiments → versions → models → explanations
        List<Experiment> experiments = experimentRepository.findByDatasetAndUserOrderByCreatedAtDesc(dataset, user);
        for (Experiment e : experiments) {
            String eId = "experiment:" + e.getId();
            nodes.add(EvidenceGraphDto.Node.builder()
                    .id(eId).type("EXPERIMENT").label(e.getName())
                    .detail(e.getTargetColumn() + " · " + e.getProblemType() + " · " + e.getStatus())
                    .meta(meta("experimentId", e.getId(), "target", e.getTargetColumn(), "problemType", e.getProblemType()))
                    .build());
            edges.add(EvidenceGraphDto.Edge.builder().from(dsId).to(eId).type("TRAINS").label("experiment").build());

            for (ExperimentVersion v : experimentVersionRepository.findByExperimentOrderByVersionNumberDesc(e)) {
                String vId = "version:" + v.getId();
                nodes.add(EvidenceGraphDto.Node.builder()
                        .id(vId).type("EXPERIMENT_VERSION").label("v" + v.getVersionNumber() + " · " + v.getBestModelName())
                        .detail(String.format("metric=%.4f · datasetVersion=%s · seed=%s · engine=%s",
                                v.getBestMetricValue() == null ? 0.0 : v.getBestMetricValue(),
                                v.getDatasetVersion(), v.getRandomSeed(), v.getEngineVersion()))
                        .meta(meta("versionId", v.getId(), "versionNumber", v.getVersionNumber(),
                                "bestModel", v.getBestModelName(), "bestMetric", v.getBestMetricValue(),
                                "datasetVersion", v.getDatasetVersion(), "seed", v.getRandomSeed()))
                        .build());
                edges.add(EvidenceGraphDto.Edge.builder().from(eId).to(vId).type("HAS_VERSION").label("version").build());

                for (ModelRun run : modelRunRepository.findByExperimentVersion(v)) {
                    String mId = "model:" + run.getId();
                    nodes.add(EvidenceGraphDto.Node.builder()
                            .id(mId).type("ML_MODEL").label(run.getModelName())
                            .detail(String.format("%s · val=%s · test=%s · seed=%s",
                                    run.getAlgorithm(), run.getValMetricsJson(), run.getTestMetricsJson(), run.getRandomSeed()))
                            .meta(meta("modelRunId", run.getId(), "algorithm", run.getAlgorithm(),
                                    "best", run.getIsBestModel(), "trainRows", run.getTrainRows(),
                                    "valRows", run.getValRows(), "testRows", run.getTestRows()))
                            .build());
                    edges.add(EvidenceGraphDto.Edge.builder().from(vId).to(mId).type("PRODUCED").label("model").build());

                    if (run.getFeatureImportancesJson() != null && !run.getFeatureImportancesJson().isBlank()) {
                        String xId = "explanation:" + run.getId();
                        List<Map<String, Object>> importances = parseList(run.getFeatureImportancesJson());
                        String topFeatures = importances.stream().limit(5)
                                .map(m -> String.valueOf(m.get("featureName")) + " ("
                                        + m.getOrDefault("relativePercentage", "–") + "%)")
                                .collect(Collectors.joining(", "));
                        nodes.add(EvidenceGraphDto.Node.builder()
                                .id(xId).type("EXPLANATION").label("Permutation importance")
                                .detail(topFeatures.isEmpty() ? "No importances" : topFeatures)
                                .meta(meta("modelRunId", run.getId(), "featureCount", importances.size()))
                                .build());
                        edges.add(EvidenceGraphDto.Edge.builder().from(mId).to(xId).type("EXPLAINS").label("XAI").build());
                    }
                }
            }
        }

        // Reports
        for (Report r : reportRepository.findByDatasetOrderByCreatedAtDesc(dataset)) {
            String rId = "report:" + r.getId();
            nodes.add(EvidenceGraphDto.Node.builder()
                    .id(rId).type("REPORT").label(truncate(r.getTitle(), 100))
                    .detail("Generated " + r.getCreatedAt())
                    .meta(meta("reportId", r.getId(), "createdAt", String.valueOf(r.getCreatedAt())))
                    .build());
            edges.add(EvidenceGraphDto.Edge.builder().from(rId).to(dsId).type("SUMMARIZES").label("dataset").build());
            if (r.getResearchQuestion() != null && nodeExists(nodes, "question:" + r.getResearchQuestion().getId()))
                edges.add(EvidenceGraphDto.Edge.builder().from(rId).to("question:" + r.getResearchQuestion().getId())
                        .type("SUMMARIZES").label("question").build());
        }

        // Recorded analysis provenance
        for (AnalysisRun run : analysisRunRepository.findByDatasetIdOrderByUpdatedAtDesc(datasetId)) {
            String aId = "run:" + run.getId();
            nodes.add(EvidenceGraphDto.Node.builder()
                    .id(aId).type("ANALYSIS_RUN").label(run.getOperation().replace('_', ' '))
                    .detail(run.getSummary())
                    .meta(meta("operation", run.getOperation(), "status", run.getStatus(),
                            "runCount", run.getRunCount(), "updatedAt", String.valueOf(run.getUpdatedAt())))
                    .build());
            edges.add(EvidenceGraphDto.Edge.builder().from(dsId).to(aId).type("EXECUTED").label("run").build());
        }

        return EvidenceGraphDto.builder()
                .datasetId(dataset.getId())
                .datasetName(dataset.getName())
                .nodes(nodes)
                .edges(edges)
                .reliability(computeReliability(dataset, experiments))
                .build();
    }

    // --- reliability scorecard ---

    private EvidenceGraphDto.ReliabilityScorecard computeReliability(Dataset dataset, List<Experiment> experiments) {
        List<EvidenceGraphDto.Check> checks = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        int earned = 0;
        int total = 0;

        // 1. Sample size
        int rows = dataset.getRowCount() == null ? 0 : dataset.getRowCount();
        total += 20;
        if (rows >= 100) { earned += 20; checks.add(check("sample_size", "Sample size", "PASS",
                rows + " rows available", 20)); }
        else if (rows >= 30) { earned += 10; checks.add(check("sample_size", "Sample size", "WARN",
                rows + " rows is small; estimates will be noisy", 20)); limitations.add("Small sample (" + rows + " rows)."); }
        else { checks.add(check("sample_size", "Sample size", "FAIL",
                rows + " rows is too small for stable inference", 20)); limitations.add("Very small sample (" + rows + " rows)."); }

        // 2. Missingness (active analytic columns)
        List<DatasetColumn> analytic = ColumnSelection.analytic(dataset);
        double avgMissing = analytic.isEmpty() ? 0.0 : analytic.stream()
                .map(c -> c.getMissingPct() == null ? 0.0 : c.getMissingPct())
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        total += 20;
        if (avgMissing < 5) { earned += 20; checks.add(check("missingness", "Missing data", "PASS",
                String.format("%.1f%% average missingness across %d analytic variables", avgMissing, analytic.size()), 20)); }
        else if (avgMissing < 20) { earned += 10; checks.add(check("missingness", "Missing data", "WARN",
                String.format("%.1f%% average missingness — check imputation assumptions", avgMissing), 20));
            limitations.add("Missing data may bias estimates."); }
        else { checks.add(check("missingness", "Missing data", "FAIL",
                String.format("%.1f%% average missingness — results are fragile", avgMissing), 20));
            limitations.add("High missingness."); }

        // 3. Multiple testing (Benjamini-Hochberg)
        List<StatisticalTest> tests = statisticalTestRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        List<Double> pValues = tests.stream()
                .filter(t -> t.getPValue() != null && !"INSUFFICIENT_SAMPLE".equals(t.getStatus()))
                .map(StatisticalTest::getPValue).sorted().toList();
        total += 15;
        int survived = benjaminiHochbergSurvivors(pValues, 0.05);
        if (pValues.size() <= 1) {
            earned += 15;
            checks.add(check("multiple_testing", "Multiple testing", "NA",
                    pValues.isEmpty() ? "No inferential tests recorded yet" : "One test — no correction needed", 15));
        } else if (survived > 0) {
            earned += 15;
            checks.add(check("multiple_testing", "Multiple testing", "PASS",
                    pValues.size() + " tests; " + survived + " survive Benjamini-Hochberg FDR at 0.05", 15));
        } else {
            earned += 7;
            checks.add(check("multiple_testing", "Multiple testing", "WARN",
                    pValues.size() + " tests; none survive Benjamini-Hochberg FDR at 0.05", 15));
            limitations.add("Multiple testing: no finding survives FDR correction.");
        }
        if (pValues.size() > 1) limitations.add("Family-wise error controlled with Benjamini-Hochberg (FDR 0.05).");

        // 4. Leakage / split discipline
        ModelRun bestRun = null;
        ExperimentVersion latestVersion = null;
        for (Experiment e : experiments) {
            List<ExperimentVersion> versions = experimentVersionRepository.findByExperimentOrderByVersionNumberDesc(e);
            if (versions.isEmpty()) continue;
            ExperimentVersion v = versions.get(0);
            List<ModelRun> runs = modelRunRepository.findByExperimentVersion(v);
            ModelRun best = runs.stream().filter(ModelRun::getIsBestModel).findFirst()
                    .orElse(runs.isEmpty() ? null : runs.get(0));
            if (best != null) { bestRun = best; latestVersion = v; break; }
        }
        total += 20;
        if (bestRun == null) {
            checks.add(check("leakage", "Train/test discipline", "NA", "No trained model yet", 20));
        } else {
            int train = bestRun.getTrainRows() == null ? 0 : bestRun.getTrainRows();
            int val = bestRun.getValRows() == null ? 0 : bestRun.getValRows();
            int test = bestRun.getTestRows() == null ? 0 : bestRun.getTestRows();
            if (train > 0 && val > 0 && test > 0) {
                earned += 20;
                checks.add(check("leakage", "Train/test discipline", "PASS",
                        String.format("Held-out split: train=%d, validation=%d, test=%d; preprocessing fitted on train", train, val, test), 20));
            } else {
                earned += 8;
                checks.add(check("leakage", "Train/test discipline", "WARN",
                        String.format("Split metadata incomplete: train=%d, validation=%d, test=%d", train, val, test), 20));
                limitations.add("Model split metadata incomplete.");
            }
        }

        // 5. Model validation (cross-validation)
        total += 15;
        String cvStatus = bestRun != null ? cvStatus(bestRun.getEvaluationJson()) : null;
        if (cvStatus == null) {
            checks.add(check("model_validation", "Model validation", "NA", "No trained model yet", 15));
        } else if ("COMPLETED".equals(cvStatus)) {
            earned += 15;
            checks.add(check("model_validation", "Model validation", "PASS",
                    "k-fold cross-validation completed on the training split", 15));
        } else {
            earned += 7;
            checks.add(check("model_validation", "Model validation", "WARN",
                    "Cross-validation status: " + cvStatus + " — rely on the held-out test metrics", 15));
            limitations.add("Cross-validation was not completed.");
        }

        // 6. Assumptions / sample per test
        total += 10;
        List<Integer> sampleSizes = tests.stream().map(StatisticalTest::getSampleSize)
                .filter(Objects::nonNull).toList();
        if (sampleSizes.isEmpty()) {
            checks.add(check("assumptions", "Test assumptions", "NA", "No statistical tests recorded yet", 10));
        } else {
            int minN = sampleSizes.stream().mapToInt(Integer::intValue).min().orElse(0);
            long withEffect = tests.stream().filter(t -> t.getEffectSize() != null).count();
            if (minN >= 15 && withEffect == tests.size()) {
                earned += 10;
                checks.add(check("assumptions", "Test assumptions", "PASS",
                        "Minimum test sample N=" + minN + "; effect sizes reported for all tests", 10));
            } else {
                earned += 5;
                checks.add(check("assumptions", "Test assumptions", "WARN",
                        "Minimum test sample N=" + minN + "; effect sizes for " + withEffect + "/" + tests.size() + " tests", 10));
                limitations.add("Some tests have small samples or missing effect sizes.");
            }
        }

        int score = total == 0 ? 0 : (int) Math.round(earned * 100.0 / total);
        String grade = score >= 85 ? "STRONG" : score >= 70 ? "GOOD" : score >= 50 ? "FAIR" : "WEAK";
        return EvidenceGraphDto.ReliabilityScorecard.builder()
                .score(score).grade(grade).checks(checks).limitations(limitations).build();
    }

    private int benjaminiHochbergSurvivors(List<Double> sortedP, double alpha) {
        int m = sortedP.size();
        int survivors = 0;
        for (int i = 0; i < m; i++) {
            double adjusted = sortedP.get(i) * m / (i + 1);
            if (adjusted <= alpha) survivors = i + 1;
        }
        return survivors;
    }

    private String cvStatus(String evaluationJson) {
        if (evaluationJson == null || evaluationJson.isBlank()) return null;
        try {
            Map<String, Object> eval = objectMapper.readValue(evaluationJson,
                    new TypeReference<Map<String, Object>>() {});
            Object cv = eval.get("crossValidation");
            if (cv instanceof Map<?, ?> map) {
                Object status = map.get("status");
                return status == null ? "UNAVAILABLE" : String.valueOf(status);
            }
            return "UNAVAILABLE";
        } catch (Exception e) {
            return "UNAVAILABLE";
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private EvidenceGraphDto.Check check(String id, String label, String status, String detail, int weight) {
        return EvidenceGraphDto.Check.builder()
                .id(id).label(label).status(status).detail(detail).weight(weight).build();
    }

    private Map<String, Object> meta(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i] != null) m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private boolean nodeExists(List<EvidenceGraphDto.Node> nodes, String id) {
        return nodes.stream().anyMatch(n -> n.getId().equals(id));
    }

    private boolean columnExists(List<EvidenceGraphDto.Node> nodes, String columnName) {
        return columnName != null && nodeExists(nodes, "column:" + columnName);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
