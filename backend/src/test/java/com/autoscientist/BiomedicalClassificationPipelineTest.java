package com.autoscientist;

import com.autoscientist.analysis.association.AssociationEngine;
import com.autoscientist.analysis.drift.DataDriftEngine;
import com.autoscientist.analysis.fairness.FairnessEngine;
import com.autoscientist.analysis.feature.FeatureEngineeringEngine;
import com.autoscientist.analysis.feature.FeatureEngineeringEngine.DatasetSplit;
import com.autoscientist.analysis.findings.QuestionAnalysisEngine;
import com.autoscientist.analysis.ml.AutoMlEngine;
import com.autoscientist.analysis.ml.AutoMlEngine.ModelBenchmarkResult;
import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.analysis.quality.DataQualityEngine;
import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.analysis.xai.ExplainabilityEngine;
import com.autoscientist.dto.drift.DatasetDriftReportDto;
import com.autoscientist.dto.ml.FairnessDto;
import com.autoscientist.dto.ml.FeatureImportanceDto;
import com.autoscientist.entity.Association;
import com.autoscientist.entity.DataQualityReport;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.entity.ResearchQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BiomedicalClassificationPipelineTest {

    private final DatasetProfiler profiler = new DatasetProfiler();
    private final DataQualityEngine qualityEngine = new DataQualityEngine();
    private final StatisticalTestEngine testEngine = new StatisticalTestEngine();
    private final AssociationEngine associationEngine = new AssociationEngine(testEngine);
    private final QuestionAnalysisEngine questionEngine = new QuestionAnalysisEngine();
    private final FeatureEngineeringEngine featureEngineeringEngine = new FeatureEngineeringEngine();
    private final AutoMlEngine autoMlEngine = new AutoMlEngine();
    private final ExplainabilityEngine explainabilityEngine = new ExplainabilityEngine();
    private final FairnessEngine fairnessEngine = new FairnessEngine();
    private final DataDriftEngine driftEngine = new DataDriftEngine();

    @Test
    @DisplayName("Complete Clinical Classification Pipeline on Biomedical Patient Dataset")
    void testBiomedicalClassificationWorkflow() throws Exception {
        Path csvPath = Paths.get("../test-data/biomedical_patient_data.csv");
        if (!Files.exists(csvPath)) {
            csvPath = Paths.get("test-data/biomedical_patient_data.csv");
        }
        byte[] csvBytes = Files.readAllBytes(csvPath);

        Dataset dataset = Dataset.builder()
                .id(2L)
                .name("Biomedical Patient Cohort")
                .originalFilename("biomedical_patient_data.csv")
                .datasetType("CLASSIFICATION")
                .build();

        // 1. Profiling
        DatasetProfiler.ProfilingResult profResult;
        try (InputStream is = new ByteArrayInputStream(csvBytes)) {
            profResult = profiler.profile(is, dataset);
        }
        assertNotNull(profResult);
        assertTrue(profResult.getRowCount() >= 25);
        assertEquals(8, profResult.getColumnCount());
        dataset.setRowCount(profResult.getRowCount());
        dataset.setColumnCount(profResult.getColumnCount());

        List<DatasetColumn> columns = profResult.getColumns();
        columns.forEach(c -> c.setDataset(dataset));
        dataset.setColumns(columns);

        DatasetColumn outcomeCol = columns.stream()
                .filter(c -> c.getColumnName().equals("outcome"))
                .findFirst().orElseThrow();
        assertEquals("CATEGORICAL", outcomeCol.getDataType());
        assertEquals(2, outcomeCol.getUniqueCount());

        // 2. Data Quality Audit
        DataQualityReport qualityReport;
        try (InputStream is = new ByteArrayInputStream(csvBytes)) {
            qualityReport = qualityEngine.evaluate(is, dataset, columns);
        }
        assertNotNull(qualityReport);
        assertTrue(qualityReport.getOverallScore() >= 80.0);

        // 3. Associations (Welch t-test & Chi-Square)
        List<Association> associations;
        try (InputStream is = new ByteArrayInputStream(csvBytes)) {
            associations = associationEngine.discoverAssociations(is, dataset, columns);
        }
        assertNotNull(associations);
        assertFalse(associations.isEmpty());

        // Check for association between systolic_bp and outcome or smoker and outcome
        boolean foundBiomarkerAssoc = associations.stream()
                .anyMatch(a -> (a.getVar1().contains("bp") || a.getVar2().contains("bp") ||
                                a.getVar1().contains("cholesterol") || a.getVar2().contains("cholesterol")));
        assertTrue(foundBiomarkerAssoc, "Biomarker associations should be discovered");

        // 4. Research Question NLP
        ResearchQuestion rqTemplate = ResearchQuestion.builder().build();
        var rqResult = questionEngine.analyzeQuestion(
                "Is systolic_bp significantly associated with patient outcome?",
                "systolic_bp", "outcome", columns, rqTemplate);
        assertNotNull(rqResult);
        assertEquals("systolic_bp", rqResult.getPredictorVariable());
        assertEquals("outcome", rqResult.getTargetVariable());

        // 5. Leak-Free Feature Engineering Split for Classification
        DatasetSplit split;
        try (InputStream is = new ByteArrayInputStream(csvBytes)) {
            split = featureEngineeringEngine.prepareDataset(is, columns, "outcome", "CLASSIFICATION");
        }
        assertNotNull(split);
        assertEquals("CLASSIFICATION", split.getProblemType());
        assertNotNull(split.getClassMap());
        assertEquals(2, split.getClassMap().size());
        assertTrue(split.isBinaryClassification());
        assertEquals(1, split.getPositiveClassIndex());
        assertNotNull(split.getPositiveClassLabel());
        assertEquals(split.getReverseClassMap().get(1), split.getPositiveClassLabel());

        // 6. AutoML Classification Benchmark (Logistic Regression, Decision Tree, Random Forest)
        List<ModelBenchmarkResult> modelResults = autoMlEngine.runAutoMlBenchmark(split);
        assertNotNull(modelResults);
        assertEquals(3, modelResults.size());

        ModelBenchmarkResult bestModel = modelResults.get(0);
        assertTrue(bestModel.isBest());
        assertNotNull(bestModel.getValMetrics().get("f1"));
        assertNotNull(bestModel.getValMetrics().get("accuracy"));
        assertTrue(bestModel.getValMetrics().get("accuracy") >= 0.60);

        // 7. Explainability (XAI)
        List<FeatureImportanceDto> importances = explainabilityEngine.computeFeatureImportance(split, bestModel);
        assertNotNull(importances);
        assertFalse(importances.isEmpty());

        // 8. Algorithmic Fairness Audit across sensitive categories
        FairnessDto fairness = fairnessEngine.evaluateFairness(columns, split, bestModel, "smoker");
        assertNotNull(fairness);
        assertNotNull(fairness.getDisparateImpactStatus());
        assertNotNull(fairness.getRecommendation());
        assertEquals(split.getPositiveClassIndex(), fairness.getPositiveClassIndex());
        assertEquals(split.getPositiveClassLabel(), fairness.getPositiveClassLabel());

        // 9. Cohort Stability / Drift
        DatasetDriftReportDto driftReport = driftEngine.analyzeDrift(
                dataset, new ByteArrayInputStream(csvBytes),
                dataset, new ByteArrayInputStream(csvBytes));
        assertNotNull(driftReport);
        assertEquals("STABLE", driftReport.getOverallStatus());
    }
}
