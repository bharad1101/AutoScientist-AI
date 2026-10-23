package com.autoscientist.service;

import com.autoscientist.analysis.core.AnalysisOperations;
import com.autoscientist.dto.dataset.WorkflowStatusDto;
import com.autoscientist.dto.dataset.WorkflowStatusDto.StageStatusDto;
import com.autoscientist.entity.*;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Workflow status derived strictly from persisted evidence:
 *
 *  - a stage is "completed" only when a real record exists (profile, quality report,
 *    recorded analysis run, hypothesis test, model run with persisted explanations,
 *    finding, report) — never because an earlier stage finished;
 *  - every locked stage carries a reason and the action that unlocks it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final DatasetRepository datasetRepository;
    private final DataQualityReportRepository dataQualityReportRepository;
    private final AssociationRepository associationRepository;
    private final ResearchQuestionRepository researchQuestionRepository;
    private final StatisticalTestRepository statisticalTestRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentVersionRepository experimentVersionRepository;
    private final ModelRunRepository modelRunRepository;
    private final FindingRepository findingRepository;
    private final ReportRepository reportRepository;
    private final AnalysisRunRepository analysisRunRepository;

    @Transactional(readOnly = true)
    public WorkflowStatusDto getWorkflowStatus(Long datasetId, User user) {
        Dataset dataset = datasetRepository.findByIdAndOwner(datasetId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found with id: " + datasetId));

        Map<String, StageStatusDto> stages = new LinkedHashMap<>();

        int rowCount = dataset.getRowCount() != null ? dataset.getRowCount() : 0;
        int columnCount = dataset.getColumnCount() != null ? dataset.getColumnCount() : 0;
        boolean hasRows = rowCount > 0;
        boolean hasColumns = columnCount > 0;
        boolean datasetValid = hasRows && hasColumns;

        // Analytic columns = non-identifier, non-excluded variables actually usable in analysis.
        Set<String> excluded = parseExcluded(dataset.getExcludedColumnsJson());
        List<DatasetColumn> analyticColumns = new ArrayList<>();
        List<DatasetColumn> numericColumns = new ArrayList<>();
        int identifierCount = 0;
        for (DatasetColumn c : dataset.getColumns()) {
            if (excluded.contains(c.getColumnName())) continue;
            if ("IDENTIFIER".equals(c.getDataType())) {
                identifierCount++;
                continue;
            }
            analyticColumns.add(c);
            if ("NUMERIC".equals(c.getDataType())) numericColumns.add(c);
        }

        Map<String, AnalysisRun> runs = latestRuns(dataset);

        // 1. Upload
        stages.put("upload", StageStatusDto.builder()
                .stageKey("upload")
                .label("Upload Dataset")
                .completed(true)
                .count(rowCount)
                .summary(String.format("%d rows, %d columns ingested", rowCount, columnCount))
                .prerequisitesMet(true)
                .build());

        // 2. Dataset Overview — completed once profiling produced typed columns
        boolean overviewDone = datasetValid && !dataset.getColumns().isEmpty();
        stages.put("overview", StageStatusDto.builder()
                .stageKey("overview")
                .label("Dataset Overview")
                .completed(overviewDone)
                .count(columnCount)
                .summary(overviewDone
                        ? String.format("%d variables profiled (%d excluded from analysis, %d identifier-type)",
                                columnCount, excluded.size(), identifierCount)
                        : "Pending profiling — upload a file with rows and columns")
                .prerequisitesMet(datasetValid)
                .prerequisiteMessage(datasetValid ? null
                        : "Dataset has no analyzable rows/columns. Upload a valid CSV or Excel file to begin.")
                .prerequisiteStage("upload")
                .build());

        // 3. Data Quality — completed only when a quality audit row exists
        Optional<DataQualityReport> qualityOpt = dataQualityReportRepository.findByDataset(dataset);
        boolean qualityDone = qualityOpt.isPresent() && qualityOpt.get().getOverallScore() != null;
        stages.put("quality", StageStatusDto.builder()
                .stageKey("quality")
                .label("Data Quality")
                .completed(qualityDone)
                .count(qualityOpt.map(q -> q.getIssues() != null ? q.getIssues().size() : 0).orElse(0))
                .summary(qualityDone
                        ? String.format("Audit score: %.1f / 100 (%d issue(s))",
                                qualityOpt.get().getOverallScore(),
                                qualityOpt.get().getIssues() != null ? qualityOpt.get().getIssues().size() : 0)
                        : "Audit not run yet")
                .prerequisitesMet(datasetValid)
                .prerequisiteMessage(datasetValid ? null : "Data quality requires a valid dataset with rows.")
                .prerequisiteStage("upload")
                .build());

        // 4. EDA — completed only when a correlation/boxplot computation actually ran
        boolean correlationsDone = runs.containsKey(AnalysisOperations.EDA_CORRELATIONS);
        boolean boxplotsDone = runs.containsKey(AnalysisOperations.EDA_BOXPLOTS);
        boolean edaDone = correlationsDone || boxplotsDone;
        boolean hasAnalyticVars = analyticColumns.size() >= 2;
        stages.put("eda", StageStatusDto.builder()
                .stageKey("eda")
                .label("Automated EDA")
                .completed(edaDone)
                .count(analyticColumns.size())
                .summary(edaDone
                        ? String.format("Computed: %s%s",
                                correlationsDone ? "correlation matrix" : "",
                                (correlationsDone && boxplotsDone) ? " + distribution plots" : (boxplotsDone ? "distribution plots" : ""))
                        : "Not run yet — open Visualizations or run correlations")
                .prerequisitesMet(datasetValid && hasAnalyticVars)
                .prerequisiteMessage((datasetValid && hasAnalyticVars) ? null
                        : (!datasetValid
                            ? "EDA requires a valid dataset with rows."
                            : "EDA needs at least two non-identifier variables. Include columns explicitly or upload a richer dataset."))
                .prerequisiteStage("overview")
                .build());

        // 5. Associations
        List<Association> associations = associationRepository.findByDatasetOrderByRankScoreDesc(dataset);
        boolean associationsDone = !associations.isEmpty();
        stages.put("associations", StageStatusDto.builder()
                .stageKey("associations")
                .label("Associations")
                .completed(associationsDone)
                .count(associations.size())
                .summary(associationsDone
                        ? associations.size() + " significant association(s) detected"
                        : "No associations computed yet")
                .prerequisitesMet(hasAnalyticVars)
                .prerequisiteMessage(hasAnalyticVars ? null
                        : "Association discovery requires at least two compatible (non-identifier) variables.")
                .prerequisiteStage("eda")
                .build());

        // 6. Research Questions
        List<ResearchQuestion> questions = researchQuestionRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        boolean questionsDone = !questions.isEmpty();
        stages.put("research", StageStatusDto.builder()
                .stageKey("research")
                .label("Research Questions")
                .completed(questionsDone)
                .count(questions.size())
                .summary(questionsDone
                        ? questions.size() + " question(s) formulated"
                        : "No questions formulated yet")
                .prerequisitesMet(hasAnalyticVars)
                .prerequisiteMessage(hasAnalyticVars ? null
                        : "Formulating a question requires at least two analysable variables.")
                .prerequisiteStage("overview")
                .build());

        // 7. Hypotheses
        int totalHypotheses = 0;
        for (ResearchQuestion q : questions) {
            if (q.getHypotheses() != null) totalHypotheses += q.getHypotheses().size();
        }
        boolean hypothesesDone = totalHypotheses > 0;
        stages.put("hypotheses", StageStatusDto.builder()
                .stageKey("hypotheses")
                .label("Hypotheses")
                .completed(hypothesesDone)
                .count(totalHypotheses)
                .summary(hypothesesDone
                        ? totalHypotheses + " hypothesis pair(s) generated"
                        : "No hypotheses formulated yet")
                .prerequisitesMet(questionsDone)
                .prerequisiteMessage(questionsDone ? null
                        : "Hypotheses require at least one formulated research question.")
                .prerequisiteStage("research")
                .build());

        // 8. Statistical Testing — completed only when a test row exists
        List<StatisticalTest> tests = statisticalTestRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        boolean testsDone = !tests.isEmpty();
        int minSample = tests.stream()
                .filter(t -> t.getSampleSize() != null)
                .mapToInt(StatisticalTest::getSampleSize).min().orElse(0);
        stages.put("statistics", StageStatusDto.builder()
                .stageKey("statistics")
                .label("Statistical Testing")
                .completed(testsDone)
                .count(tests.size())
                .summary(testsDone
                        ? String.format("%d test(s) executed%s", tests.size(),
                                minSample > 0 ? " (smallest sample n=" + minSample + ")" : "")
                        : "No tests executed yet")
                .prerequisitesMet(hypothesesDone)
                .prerequisiteMessage(hypothesesDone ? null
                        : "Statistical testing requires at least one formulated hypothesis.")
                .prerequisiteStage("hypotheses")
                .build());

        // 9. Machine Learning — completed when a model run exists for this dataset
        List<Experiment> experiments = experimentRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        long modelRunCount = 0;
        for (Experiment e : experiments) {
            if (e.getVersions() == null) continue;
            for (ExperimentVersion v : e.getVersions()) {
                modelRunCount += modelRunRepository.findByExperimentVersion(v).size();
            }
        }
        boolean mlDone = modelRunCount > 0;
        boolean mlVarsOk = analyticColumns.size() >= 2;
        stages.put("ml", StageStatusDto.builder()
                .stageKey("ml")
                .label("Machine Learning")
                .completed(mlDone)
                .count((int) modelRunCount)
                .summary(mlDone
                        ? String.format("%d experiment(s), %d model run(s) recorded", experiments.size(), modelRunCount)
                        : "No models trained yet")
                .prerequisitesMet(mlVarsOk)
                .prerequisiteMessage(mlVarsOk ? null
                        : "Machine learning needs a target plus at least one feature. Identifier columns are excluded automatically — include columns explicitly if they should be used.")
                .prerequisiteStage("overview")
                .build());

        // 10. Visualizations — completed only when a chart computation actually ran
        boolean groupedDone = runs.containsKey(AnalysisOperations.EDA_GROUPED);
        boolean visualsDone = (correlationsDone && boxplotsDone) || groupedDone;
        stages.put("visualizations", StageStatusDto.builder()
                .stageKey("visualizations")
                .label("Visualizations")
                .completed(visualsDone)
                .count(analyticColumns.size())
                .summary(visualsDone
                        ? String.format("Charts rendered: %d variable(s)%s", analyticColumns.size(),
                                groupedDone ? ", grouped comparison" : "")
                        : "No charts computed yet")
                .prerequisitesMet(datasetValid && !analyticColumns.isEmpty())
                .prerequisiteMessage((datasetValid && !analyticColumns.isEmpty()) ? null
                        : "Charts require at least one non-identifier variable.")
                .prerequisiteStage("eda")
                .build());

        // 11. Explainability/XAI — completed only when feature importances are persisted
        boolean importancesPersisted = false;
        String explainedModel = null;
        for (Experiment e : experiments) {
            if (e.getVersions() == null) continue;
            for (ExperimentVersion v : e.getVersions()) {
                for (ModelRun mr : modelRunRepository.findByExperimentVersion(v)) {
                    if (mr.getFeatureImportancesJson() != null && !mr.getFeatureImportancesJson().isBlank()
                            && !"[]".equals(mr.getFeatureImportancesJson().trim())) {
                        importancesPersisted = true;
                        explainedModel = mr.getModelName();
                    }
                }
            }
        }
        stages.put("xai", StageStatusDto.builder()
                .stageKey("xai")
                .label("Explainability/XAI")
                .completed(importancesPersisted)
                .count(importancesPersisted ? 1 : 0)
                .summary(importancesPersisted
                        ? "Permutation importance, fairness audit & what-if simulator available for " + explainedModel
                        : (mlDone
                            ? "A model exists but no explanation has been persisted — re-run training to compute feature importances"
                            : "No model available to explain"))
                .prerequisitesMet(mlDone)
                .prerequisiteMessage(mlDone ? null
                        : "Model explanation is unavailable because no model has been trained.")
                .prerequisiteStage("ml")
                .build());

        // 12. Findings
        List<Finding> findings = findingRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        boolean findingsDone = !findings.isEmpty();
        stages.put("findings", StageStatusDto.builder()
                .stageKey("findings")
                .label("Findings")
                .completed(findingsDone)
                .count(findings.size())
                .summary(findingsDone
                        ? findings.size() + " scientific finding(s) synthesized"
                        : "No findings synthesized yet")
                .prerequisitesMet(testsDone || mlDone)
                .prerequisiteMessage((testsDone || mlDone) ? null
                        : "Scientific findings require statistical tests or predictive model evaluations.")
                .prerequisiteStage(testsDone ? "statistics" : "ml")
                .build());

        // 13. Report
        List<Report> reports = reportRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        boolean reportDone = !reports.isEmpty();
        stages.put("report", StageStatusDto.builder()
                .stageKey("report")
                .label("Report")
                .completed(reportDone)
                .count(reports.size())
                .summary(reportDone
                        ? reports.size() + " scientific report(s) generated"
                        : "Report not yet generated — analyses that have not been run will be listed as such")
                .prerequisitesMet(datasetValid)
                .prerequisiteMessage(datasetValid ? null : "Reporting requires a valid dataset.")
                .prerequisiteStage("overview")
                .build());

        // Recommended next stage: first incomplete stage whose prerequisites are met;
        // if none, the first incomplete stage (so the user sees what is blocked and why).
        String recommended = "report";
        String[] stageSequence = {"overview", "quality", "eda", "associations", "research", "hypotheses",
                "statistics", "ml", "visualizations", "xai", "findings", "report"};
        String firstIncomplete = null;
        for (String sKey : stageSequence) {
            StageStatusDto st = stages.get(sKey);
            if (st == null || st.isCompleted()) continue;
            if (firstIncomplete == null) firstIncomplete = sKey;
            if (st.isPrerequisitesMet()) {
                recommended = sKey;
                break;
            }
        }
        if (firstIncomplete == null) recommended = "report";

        return WorkflowStatusDto.builder()
                .datasetId(dataset.getId())
                .datasetName(dataset.getName())
                .rowCount(rowCount)
                .columnCount(columnCount)
                .stages(stages)
                .currentRecommendedStage(recommended)
                .build();
    }

    private Map<String, AnalysisRun> latestRuns(Dataset dataset) {
        Map<String, AnalysisRun> map = new HashMap<>();
        for (AnalysisRun run : analysisRunRepository.findByDatasetIdOrderByUpdatedAtDesc(dataset.getId())) {
            map.putIfAbsent(run.getOperation(), run);
        }
        return map;
    }

    private Set<String> parseExcluded(String json) {
        if (json == null || json.isBlank()) return Collections.emptySet();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> list = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
