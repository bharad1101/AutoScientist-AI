package com.autoscientist;

import com.autoscientist.analysis.feature.FeatureEngineeringEngine.DatasetSplit;
import com.autoscientist.analysis.ml.AutoMlEngine;
import com.autoscientist.analysis.ml.AutoMlEngine.ModelBenchmarkResult;
import com.autoscientist.analysis.ml.WhatIfEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AutoMlAndWhatIfRegressionTest {

    private final AutoMlEngine autoMlEngine = new AutoMlEngine();

    @Test
    @DisplayName("AutoML Classification Evaluation: Generates confusion matrix, calibration, and cross-validation")
    void testClassificationEvaluationDiagnostics() {
        int nTrain = 80, nVal = 20, nTest = 20;
        double[][] xTrain = new double[nTrain][2];
        double[] yTrain = new double[nTrain];
        Random rnd = new Random(42);

        for (int i = 0; i < nTrain; i++) {
            xTrain[i][0] = rnd.nextDouble() * 10 - 5;
            xTrain[i][1] = rnd.nextDouble() * 10 - 5;
            yTrain[i] = (xTrain[i][0] + xTrain[i][1] > 0) ? 1.0 : 0.0;
        }

        double[][] xVal = new double[nVal][2];
        double[] yVal = new double[nVal];
        for (int i = 0; i < nVal; i++) {
            xVal[i][0] = rnd.nextDouble() * 10 - 5;
            xVal[i][1] = rnd.nextDouble() * 10 - 5;
            yVal[i] = (xVal[i][0] + xVal[i][1] > 0) ? 1.0 : 0.0;
        }

        double[][] xTest = new double[nTest][2];
        double[] yTest = new double[nTest];
        for (int i = 0; i < nTest; i++) {
            xTest[i][0] = rnd.nextDouble() * 10 - 5;
            xTest[i][1] = rnd.nextDouble() * 10 - 5;
            yTest[i] = (xTest[i][0] + xTest[i][1] > 0) ? 1.0 : 0.0;
        }

        DatasetSplit split = new DatasetSplit(
                xTrain, yTrain, xVal, yVal, xTest, yTest,
                List.of("feat1", "feat2"),
                "outcome", "CLASSIFICATION",
                Map.of("No", 0, "Yes", 1), Map.of(0, "No", 1, "Yes"), Map.of()
        );

        List<ModelBenchmarkResult> results = autoMlEngine.runAutoMlBenchmark(split);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        ModelBenchmarkResult bestModel = results.get(0);
        Map<String, Object> eval = bestModel.getEvaluation();
        assertNotNull(eval, "Evaluation map should not be null");

        // 1. Confusion Matrix
        @SuppressWarnings("unchecked")
        Map<String, Object> cm = (Map<String, Object>) eval.get("confusionMatrix");
        assertNotNull(cm, "Confusion matrix must be present");
        assertTrue(cm.containsKey("truePositives"));
        assertTrue(cm.containsKey("falsePositives"));
        assertTrue(cm.containsKey("trueNegatives"));
        assertTrue(cm.containsKey("falseNegatives"));
        assertTrue(cm.containsKey("rows"));

        // 2. Cross Validation
        @SuppressWarnings("unchecked")
        Map<String, Object> cv = (Map<String, Object>) eval.get("crossValidation");
        assertNotNull(cv, "Cross-validation must be present");
        assertTrue(cv.containsKey("mean"));
        assertTrue(cv.containsKey("folds"));

        // 3. Calibration / Brier Score
        @SuppressWarnings("unchecked")
        Map<String, Object> cal = (Map<String, Object>) eval.get("calibration");
        assertNotNull(cal, "Calibration analysis must be present");
        assertTrue(cal.containsKey("brierScore"));
        assertTrue(cal.containsKey("bins"));

        // 4. Per-Class Metrics
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> perClass = (List<Map<String, Object>>) eval.get("perClass");
        assertNotNull(perClass, "Per-class metrics must be present");
        assertFalse(perClass.isEmpty());
        assertTrue(perClass.get(0).containsKey("label"));
        assertTrue(perClass.get(0).containsKey("precision"));
        assertTrue(perClass.get(0).containsKey("recall"));
        assertTrue(perClass.get(0).containsKey("f1"));
    }

    @Test
    @DisplayName("AutoML Regression Evaluation: Generates residuals summary, points, and cross-validation")
    void testRegressionEvaluationDiagnostics() {
        int nTrain = 60, nVal = 20, nTest = 20;
        double[][] xTrain = new double[nTrain][2];
        double[] yTrain = new double[nTrain];
        Random rnd = new Random(789);

        for (int i = 0; i < nTrain; i++) {
            xTrain[i][0] = rnd.nextDouble() * 10;
            xTrain[i][1] = rnd.nextDouble() * 5;
            yTrain[i] = 3.0 * xTrain[i][0] - 2.0 * xTrain[i][1] + rnd.nextGaussian() * 0.2;
        }

        double[][] xVal = new double[nVal][2];
        double[] yVal = new double[nVal];
        for (int i = 0; i < nVal; i++) {
            xVal[i][0] = rnd.nextDouble() * 10;
            xVal[i][1] = rnd.nextDouble() * 5;
            yVal[i] = 3.0 * xVal[i][0] - 2.0 * xVal[i][1];
        }

        double[][] xTest = new double[nTest][2];
        double[] yTest = new double[nTest];
        for (int i = 0; i < nTest; i++) {
            xTest[i][0] = rnd.nextDouble() * 10;
            xTest[i][1] = rnd.nextDouble() * 5;
            yTest[i] = 3.0 * xTest[i][0] - 2.0 * xTest[i][1];
        }

        DatasetSplit split = new DatasetSplit(
                xTrain, yTrain, xVal, yVal, xTest, yTest,
                List.of("input_a", "input_b"),
                "target_y", "REGRESSION",
                null, null, Map.of()
        );

        List<ModelBenchmarkResult> results = autoMlEngine.runAutoMlBenchmark(split);
        assertNotNull(results);

        ModelBenchmarkResult bestModel = results.get(0);
        Map<String, Object> eval = bestModel.getEvaluation();
        assertNotNull(eval);

        // Residual summary
        @SuppressWarnings("unchecked")
        Map<String, Object> residuals = (Map<String, Object>) eval.get("residualSummary");
        assertNotNull(residuals, "Residual summary must be present for regression");
        assertTrue(residuals.containsKey("meanError"));
        assertTrue(residuals.containsKey("mae"));
        assertTrue(eval.containsKey("residualPoints"));
        assertTrue(eval.containsKey("predictionPoints"));

        // Cross validation
        @SuppressWarnings("unchecked")
        Map<String, Object> cv = (Map<String, Object>) eval.get("crossValidation");
        assertNotNull(cv, "Cross-validation must be present for regression");
        assertTrue(cv.containsKey("mean"));
    }

    @Test
    @DisplayName("WhatIfEngine: Schema parsing, raw feature encoding, imputation, and warnings")
    void testWhatIfEngineEncodingAndImputation() {
        Map<String, Object> preproc = new LinkedHashMap<>();
        List<Map<String, Object>> rawFeatures = new ArrayList<>();

        Map<String, Object> f1 = new LinkedHashMap<>();
        f1.put("name", "age");
        f1.put("kind", "NUMERIC");
        f1.put("imputeValue", 30.0);
        f1.put("min", 18.0);
        f1.put("max", 80.0);
        f1.put("transformedNames", List.of("age"));
        rawFeatures.add(f1);

        Map<String, Object> f2 = new LinkedHashMap<>();
        f2.put("name", "tier");
        f2.put("kind", "CATEGORICAL");
        f2.put("imputeValue", "Gold");
        f2.put("categories", List.of("Silver", "Gold", "Platinum"));
        f2.put("transformedNames", List.of("tier_Silver", "tier_Gold", "tier_Platinum"));
        rawFeatures.add(f2);

        preproc.put("rawFeatures", rawFeatures);

        // 1. Read schema
        List<WhatIfEngine.RawFeature> schema = WhatIfEngine.readSchema(preproc);
        assertEquals(2, schema.size());
        assertEquals("age", schema.get(0).name);
        assertEquals("NUMERIC", schema.get(0).kind);
        assertEquals(30.0, schema.get(0).imputeValue);
        assertEquals("tier", schema.get(1).name);
        assertEquals("CATEGORICAL", schema.get(1).kind);

        List<String> transformedNames = List.of("age", "tier_Silver", "tier_Gold", "tier_Platinum");
        List<Double> mean = List.of(35.0, 0.33, 0.33, 0.33);
        List<Double> std = List.of(10.0, 0.47, 0.47, 0.47);

        // 2. Encode with partial inputs: age supplied as 45, tier missing (should impute Gold)
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("age", 45.0);
        inputs.put("unrelated_col", "ignored_value");

        WhatIfEngine.Encoded encoded = WhatIfEngine.encode(schema, transformedNames, mean, std, inputs);
        assertNotNull(encoded);
        assertEquals(4, encoded.vector.length);

        // Age standardized: (45 - 35) / 10 = 1.0
        assertEquals(1.0, encoded.vector[0], 1e-4);

        // Tier should impute Gold: tier_Silver = 0, tier_Gold = 1, tier_Platinum = 0
        assertTrue(encoded.vector[2] > 0, "Gold tier should be positive after standardization");
        assertTrue(encoded.vector[1] < 0, "Silver tier should be negative after standardization");

        // Unrecognised input noted
        assertTrue(encoded.unrecognised.contains("unrelated_col"));

        // 3. Encode out-of-range value triggers warning
        Map<String, Object> outOfRangeInputs = Map.of("age", 150.0);
        WhatIfEngine.Encoded outOfRangeEncoded = WhatIfEngine.encode(schema, transformedNames, mean, std, outOfRangeInputs);
        assertFalse(outOfRangeEncoded.warnings.isEmpty(), "Out of range age should emit an input warning");
    }

    @Test
    @DisplayName("Feature Effect Grid Simulation: Numeric and categorical marginal response curves")
    void testFeatureEffectCurveGeneration() {
        int nTrain = 100, nVal = 20, nTest = 20;
        double[][] xTrain = new double[nTrain][2];
        double[] yTrain = new double[nTrain];
        Random rnd = new Random(123);

        for (int i = 0; i < nTrain; i++) {
            xTrain[i][0] = 20.0 + rnd.nextDouble() * 60.0; // age [20, 80]
            xTrain[i][1] = 100.0 + rnd.nextDouble() * 150.0; // cholesterol [100, 250]
            // Ground truth: linear outcome = 0.5 * age + 0.1 * chol
            yTrain[i] = 0.5 * xTrain[i][0] + 0.1 * xTrain[i][1] + rnd.nextGaussian() * 0.1;
        }

        double[][] xVal = new double[nVal][2];
        double[] yVal = new double[nVal];
        for (int i = 0; i < nVal; i++) {
            xVal[i][0] = 20.0 + rnd.nextDouble() * 60.0;
            xVal[i][1] = 100.0 + rnd.nextDouble() * 150.0;
            yVal[i] = 0.5 * xVal[i][0] + 0.1 * xVal[i][1];
        }

        double[][] xTest = new double[nTest][2];
        double[] yTest = new double[nTest];
        for (int i = 0; i < nTest; i++) {
            xTest[i][0] = 20.0 + rnd.nextDouble() * 60.0;
            xTest[i][1] = 100.0 + rnd.nextDouble() * 150.0;
            yTest[i] = 0.5 * xTest[i][0] + 0.1 * xTest[i][1];
        }

        DatasetSplit split = new DatasetSplit(
                xTrain, yTrain, xVal, yVal, xTest, yTest,
                List.of("age", "cholesterol"),
                "risk_score", "REGRESSION",
                null, null, Map.of()
        );

        List<ModelBenchmarkResult> results = autoMlEngine.runAutoMlBenchmark(split);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        AutoMlEngine.PredictiveModel model = results.get(0).getTrainedModel();
        assertNotNull(model);

        // Build schema
        List<WhatIfEngine.RawFeature> schema = new ArrayList<>();
        WhatIfEngine.RawFeature rfAge = new WhatIfEngine.RawFeature();
        rfAge.name = "age";
        rfAge.kind = "NUMERIC";
        rfAge.min = 20.0;
        rfAge.max = 80.0;
        rfAge.imputeValue = 50.0;
        rfAge.transformedNames = List.of("age");
        schema.add(rfAge);

        WhatIfEngine.RawFeature rfChol = new WhatIfEngine.RawFeature();
        rfChol.name = "cholesterol";
        rfChol.kind = "NUMERIC";
        rfChol.min = 100.0;
        rfChol.max = 250.0;
        rfChol.imputeValue = 175.0;
        rfChol.transformedNames = List.of("cholesterol");
        schema.add(rfChol);

        List<String> transformedNames = List.of("age", "cholesterol");
        List<Double> mean = List.of(0.0, 0.0);
        List<Double> std = List.of(1.0, 1.0);

        // Generate 15-point grid for age
        double prevPred = -1e9;
        boolean strictlyIncreasing = true;
        for (int i = 0; i < 15; i++) {
            double ageVal = 20.0 + (80.0 - 20.0) * i / 14.0;
            Map<String, Object> inputs = Map.of("age", ageVal);
            WhatIfEngine.Encoded enc = WhatIfEngine.encode(schema, transformedNames, mean, std, inputs);
            double pred = model.predictSingle(enc.vector);
            if (i > 0 && pred <= prevPred) {
                strictlyIncreasing = false;
            }
            prevPred = pred;
        }

        assertTrue(strictlyIncreasing, "Predicted outcome should increase monotonically as age increases in this linear benchmark model");
    }
}
