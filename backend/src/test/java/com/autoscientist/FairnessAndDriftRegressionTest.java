package com.autoscientist;

import com.autoscientist.analysis.drift.DataDriftEngine;
import com.autoscientist.analysis.fairness.FairnessEngine;
import com.autoscientist.analysis.feature.FeatureEngineeringEngine.DatasetSplit;
import com.autoscientist.analysis.ml.AutoMlEngine;
import com.autoscientist.analysis.ml.AutoMlEngine.ModelBenchmarkResult;
import com.autoscientist.dto.drift.ColumnDriftDto;
import com.autoscientist.dto.drift.DatasetDriftReportDto;
import com.autoscientist.dto.ml.FairnessDto;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FairnessAndDriftRegressionTest {

    private final FairnessEngine fairnessEngine = new FairnessEngine();
    private final DataDriftEngine dataDriftEngine = new DataDriftEngine();

    @Test
    @DisplayName("FairnessEngine: Populates group sample sizes, rates, and enforces 80% rule")
    void testFairnessFourFifthsRuleAndSampleSizes() {
        int nVal = 40;
        double[][] xVal = new double[nVal][2];
        double[] yVal = new double[nVal];
        double[] mockPredictions = new double[nVal];

        // Group A: 20 rows, 16 predicted positive (80% positive rate)
        // Group B: 20 rows, 8 predicted positive (40% positive rate) -> ratio = 40/80 = 0.50 (< 0.80 -> Disparate Impact)
        List<String> rawGender = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rawGender.add("GroupA");
            xVal[i][0] = 1.0; // GroupA active
            xVal[i][1] = 0.0;
            mockPredictions[i] = (i < 16) ? 1.0 : 0.0;
            yVal[i] = mockPredictions[i];
        }
        for (int i = 20; i < 40; i++) {
            rawGender.add("GroupB");
            xVal[i][0] = 0.0;
            xVal[i][1] = 1.0; // GroupB active
            mockPredictions[i] = (i < 28) ? 1.0 : 0.0;
            yVal[i] = mockPredictions[i];
        }

        DatasetSplit split = new DatasetSplit(
                new double[10][2], new double[10],
                xVal, yVal,
                new double[10][2], new double[10],
                List.of("sensitive_group_GroupA", "sensitive_group_GroupB"),
                "target", "CLASSIFICATION",
                Map.of("0", 0, "1", 1), Map.of(0, "0", 1, "1"),
                Map.of("sensitive_group", rawGender)
        );

        AutoMlEngine.PredictiveModel mockPredictiveModel = new AutoMlEngine.PredictiveModel() {
            @Override
            public double predictSingle(double[] x) {
                return 1.0;
            }

            @Override
            public double[] predict(double[][] X) {
                return mockPredictions;
            }

            @Override
            public double[] getWeights() {
                return new double[]{1.0, 1.0};
            }
        };

        ModelBenchmarkResult mockModel = new ModelBenchmarkResult(
                "TestModel", "LOGISTIC_REGRESSION", Map.of(),
                Map.of("accuracy", 0.85), Map.of("accuracy", 0.85), Map.of("accuracy", 0.85),
                true, mockPredictiveModel, Map.of()
        );

        DatasetColumn sensitiveCol = DatasetColumn.builder()
                .columnName("sensitive_group")
                .dataType("CATEGORICAL")
                .build();

        FairnessDto fairness = fairnessEngine.evaluateFairness(
                List.of(sensitiveCol), split, mockModel, "sensitive_group"
        );

        assertNotNull(fairness);
        assertEquals("FLAGGED", fairness.getDisparateImpactStatus());
        assertTrue(fairness.getDemographicParityRatio() < 0.80, "Ratio should be < 0.80");

        // Verify groupSampleSizes
        Map<String, Integer> sizes = fairness.getGroupSampleSizes();
        assertNotNull(sizes);
        assertEquals(20, sizes.get("GroupA"));
        assertEquals(20, sizes.get("GroupB"));

        // Verify group positive rates
        Map<String, Double> posRates = fairness.getGroupPositiveRates();
        assertNotNull(posRates);
        assertEquals(0.80, posRates.get("GroupA"), 1e-4);
        assertEquals(0.40, posRates.get("GroupB"), 1e-4);
    }

    @Test
    @DisplayName("DataDriftEngine: Computes KS test, t-test, PSI, bin edges, and proportions")
    void testDriftEngineKsTestAndBinProportions() throws Exception {
        // Reference dataset
        StringBuilder refCsv = new StringBuilder("val,category\n");
        for (int i = 1; i <= 50; i++) {
            refCsv.append(i).append(",typeA\n");
        }

        // Target dataset with identical distribution
        StringBuilder identicalTarCsv = new StringBuilder("val,category\n");
        for (int i = 1; i <= 50; i++) {
            identicalTarCsv.append(i).append(",typeA\n");
        }

        // Shifted target dataset
        StringBuilder shiftedTarCsv = new StringBuilder("val,category\n");
        for (int i = 1; i <= 50; i++) {
            shiftedTarCsv.append(i + 100).append(",typeA\n");
        }

        DatasetColumn colVal = DatasetColumn.builder().columnName("val").dataType("NUMERIC").build();
        DatasetColumn colCat = DatasetColumn.builder().columnName("category").dataType("CATEGORICAL").build();

        Dataset ref = Dataset.builder().id(1L).name("Reference").columns(List.of(colVal, colCat)).build();
        Dataset identicalTar = Dataset.builder().id(2L).name("Identical").columns(List.of(colVal, colCat)).build();
        Dataset shiftedTar = Dataset.builder().id(3L).name("Shifted").columns(List.of(colVal, colCat)).build();

        // 1. Test identical
        DatasetDriftReportDto cleanReport = dataDriftEngine.analyzeDrift(
                ref, new ByteArrayInputStream(refCsv.toString().getBytes(StandardCharsets.UTF_8)),
                identicalTar, new ByteArrayInputStream(identicalTarCsv.toString().getBytes(StandardCharsets.UTF_8))
        );

        ColumnDriftDto cleanVal = cleanReport.getColumnDrifts().stream()
                .filter(c -> "val".equals(c.getColumnName()))
                .findFirst().orElseThrow();

        assertEquals("NO_DRIFT", cleanVal.getDriftStatus());
        assertEquals(0.0, cleanVal.getPsiScore(), 0.05);
        assertNotNull(cleanVal.getKsStatistic());
        assertTrue(cleanVal.getKsStatistic() < 0.1, "KS statistic should be near zero for identical distributions");
        assertNotNull(cleanVal.getBinEdges());
        assertNotNull(cleanVal.getReferenceBinProportions());
        assertNotNull(cleanVal.getTargetBinProportions());
        assertEquals(cleanVal.getReferenceBinProportions().size(), cleanVal.getTargetBinProportions().size());

        // 2. Test shifted
        DatasetDriftReportDto shiftedReport = dataDriftEngine.analyzeDrift(
                ref, new ByteArrayInputStream(refCsv.toString().getBytes(StandardCharsets.UTF_8)),
                shiftedTar, new ByteArrayInputStream(shiftedTarCsv.toString().getBytes(StandardCharsets.UTF_8))
        );

        ColumnDriftDto shiftedVal = shiftedReport.getColumnDrifts().stream()
                .filter(c -> "val".equals(c.getColumnName()))
                .findFirst().orElseThrow();

        assertEquals("SIGNIFICANT_DRIFT", shiftedVal.getDriftStatus());
        assertTrue(shiftedVal.getPsiScore() >= 0.25);
        assertTrue(shiftedVal.getKsStatistic() > 0.8, "KS statistic should be high for completely shifted data");
        assertTrue(shiftedVal.getKsPValue() < 0.01, "KS p-value should be significant for shifted data");
    }
}
