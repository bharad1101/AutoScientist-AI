package com.autoscientist.analysis.ml;

import com.autoscientist.analysis.feature.FeatureEngineeringEngine.DatasetSplit;
import org.apache.commons.math3.linear.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AutoMlEngine {

    /** Max number of curve points persisted per ROC/PR curve (keeps payloads bounded). */
    private static final int MAX_CURVE_POINTS = 100;
    /** k-fold cross-validation is skipped above this many training rows (retraining cost). */
    private static final int CV_MAX_ROWS = 2000;
    private static final int CV_MIN_ROWS = 30;

    public static class ModelBenchmarkResult {
        private final String modelName;
        private final String algorithm;
        private final Map<String, Object> hyperparameters;
        private final Map<String, Double> trainMetrics;
        private final Map<String, Double> valMetrics;
        private final Map<String, Double> testMetrics;
        private final boolean isBest;
        private final PredictiveModel trainedModel;
        private final Map<String, Object> evaluation; // ROC/PR/confusion/cv details

        public ModelBenchmarkResult(String modelName, String algorithm,
                                    Map<String, Object> hyperparameters,
                                    Map<String, Double> trainMetrics,
                                    Map<String, Double> valMetrics,
                                    Map<String, Double> testMetrics,
                                    boolean isBest,
                                    PredictiveModel trainedModel) {
            this(modelName, algorithm, hyperparameters, trainMetrics, valMetrics, testMetrics, isBest, trainedModel, Collections.emptyMap());
        }

        public ModelBenchmarkResult(String modelName, String algorithm,
                                    Map<String, Object> hyperparameters,
                                    Map<String, Double> trainMetrics,
                                    Map<String, Double> valMetrics,
                                    Map<String, Double> testMetrics,
                                    boolean isBest,
                                    PredictiveModel trainedModel,
                                    Map<String, Object> evaluation) {
            this.modelName = modelName;
            this.algorithm = algorithm;
            this.hyperparameters = hyperparameters;
            this.trainMetrics = trainMetrics;
            this.valMetrics = valMetrics;
            this.testMetrics = testMetrics;
            this.isBest = isBest;
            this.trainedModel = trainedModel;
            this.evaluation = evaluation != null ? evaluation : Collections.emptyMap();
        }

        public String getModelName() { return modelName; }
        public String getAlgorithm() { return algorithm; }
        public Map<String, Object> getHyperparameters() { return hyperparameters; }
        public Map<String, Double> getTrainMetrics() { return trainMetrics; }
        public Map<String, Double> getValMetrics() { return valMetrics; }
        public Map<String, Double> getTestMetrics() { return testMetrics; }
        public boolean isBest() { return isBest; }
        public PredictiveModel getTrainedModel() { return trainedModel; }
        public Map<String, Object> getEvaluation() { return evaluation; }
    }

    public interface PredictiveModel {
        double predictSingle(double[] x);
        double[] predict(double[][] X);
        double[] getWeights(); // for linear models

        /**
         * Probability/decision score for the positive class (classification only).
         * Models that cannot produce a calibrated score return NaN, which callers
         * must treat as "unavailable" rather than guessing.
         */
        default double predictProba(double[] x) { return Double.NaN; }
    }

    /** Retrains a model family on an alternate split; used for cross-validation. */
    @FunctionalInterface
    private interface ModelTrainer {
        PredictiveModel train(DatasetSplit split);
    }

    public List<ModelBenchmarkResult> runAutoMlBenchmark(DatasetSplit split) {
        String problemType = split.getProblemType();
        List<ModelBenchmarkResult> results = new ArrayList<>();

        if (problemType.equals("REGRESSION")) {
            // 1. OLS / Ridge Linear Regression
            ModelBenchmarkResult ridge = trainRidgeRegression(split, 0.1);
            results.add(ridge);

            // 2. Ridge with higher regularization
            ModelBenchmarkResult ridge2 = trainRidgeRegression(split, 1.0);
            results.add(ridge2);

            // 3. Decision Tree Regressor
            ModelBenchmarkResult dt = trainDecisionTreeRegressor(split, 4);
            results.add(dt);

            // Rank by validation R-squared descending
            results.sort((a, b) -> Double.compare(
                    b.getValMetrics().getOrDefault("r2", -999.0),
                    a.getValMetrics().getOrDefault("r2", -999.0)
            ));

        } else {
            // Classification
            // 1. Logistic Regression
            ModelBenchmarkResult logReg = trainLogisticRegression(split, 0.05, 300);
            results.add(logReg);

            // 2. Decision Tree Classifier
            ModelBenchmarkResult dt = trainDecisionTreeClassifier(split, 4);
            results.add(dt);

            // 3. Random Forest Classifier
            ModelBenchmarkResult rf = trainRandomForestClassifier(split, 10, 4);
            results.add(rf);

            // Rank by validation F1-score descending
            results.sort((a, b) -> Double.compare(
                    b.getValMetrics().getOrDefault("f1", 0.0),
                    a.getValMetrics().getOrDefault("f1", 0.0)
            ));
        }

        // Tag best model
        List<ModelBenchmarkResult> finalResults = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            ModelBenchmarkResult res = results.get(i);
            finalResults.add(new ModelBenchmarkResult(
                    res.getModelName(),
                    res.getAlgorithm(),
                    res.getHyperparameters(),
                    res.getTrainMetrics(),
                    res.getValMetrics(),
                    res.getTestMetrics(),
                    i == 0, // Top model is best
                    res.getTrainedModel(),
                    res.getEvaluation()
            ));
        }

        return finalResults;
    }

    // --- REGRESSION MODELS ---

    private ModelBenchmarkResult trainRidgeRegression(DatasetSplit split, double lambda) {
        ModelBenchmarkResult base = trainRidgeRegressionCore(split, lambda);
        return withEvaluation(split, base, s -> trainRidgeRegressionCore(s, lambda).getTrainedModel());
    }

    private ModelBenchmarkResult trainRidgeRegressionCore(DatasetSplit split, double lambda) {
        int n = split.getXTrain().length;
        int p = split.getFeatureNames().size();

        // Solve (X^T X + lambda I) w = X^T y
        RealMatrix X = MatrixUtils.createRealMatrix(split.getXTrain());
        RealVector y = MatrixUtils.createRealVector(split.getYTrain());

        RealMatrix XtX = X.transpose().multiply(X);
        RealMatrix lambdaI = MatrixUtils.createRealIdentityMatrix(p).scalarMultiply(lambda * n);
        RealMatrix A = XtX.add(lambdaI);

        DecompositionSolver solver = new SingularValueDecomposition(A).getSolver();
        RealVector w = solver.solve(X.transpose().operate(y));
        double[] weights = w.toArray();

        PredictiveModel model = new PredictiveModel() {
            @Override
            public double predictSingle(double[] x) {
                double pred = 0;
                for (int i = 0; i < x.length; i++) pred += x[i] * weights[i];
                return pred;
            }
            @Override
            public double[] predict(double[][] X_in) {
                double[] preds = new double[X_in.length];
                for (int i = 0; i < X_in.length; i++) preds[i] = predictSingle(X_in[i]);
                return preds;
            }
            @Override
            public double[] getWeights() { return weights; }
        };

        Map<String, Object> params = new HashMap<>();
        params.put("regularization_lambda", lambda);
        params.put("solver", "SVD Ridge");

        Map<String, Double> trainMetrics = evalRegression(model.predict(split.getXTrain()), split.getYTrain());
        Map<String, Double> valMetrics = evalRegression(model.predict(split.getXVal()), split.getYVal());
        Map<String, Double> testMetrics = evalRegression(model.predict(split.getXTest()), split.getYTest());

        String name = String.format("Ridge Regression (lambda=%.2f)", lambda);
        return new ModelBenchmarkResult(name, "RidgeRegression", params, trainMetrics, valMetrics, testMetrics, false, model);
    }

    private ModelBenchmarkResult trainDecisionTreeRegressor(DatasetSplit split, int maxDepth) {
        ModelBenchmarkResult base = trainDecisionTreeRegressorCore(split, maxDepth);
        return withEvaluation(split, base, s -> trainDecisionTreeRegressorCore(s, maxDepth).getTrainedModel());
    }

    private ModelBenchmarkResult trainDecisionTreeRegressorCore(DatasetSplit split, int maxDepth) {
        DecisionTreeNode root = buildRegressionTree(split.getXTrain(), split.getYTrain(), 0, maxDepth);

        PredictiveModel model = new PredictiveModel() {
            @Override
            public double predictSingle(double[] x) {
                return predictRegressionTree(root, x);
            }
            @Override
            public double[] predict(double[][] X_in) {
                double[] preds = new double[X_in.length];
                for (int i = 0; i < X_in.length; i++) preds[i] = predictSingle(X_in[i]);
                return preds;
            }
            @Override
            public double[] getWeights() { return new double[0]; }
        };

        Map<String, Object> params = new HashMap<>();
        params.put("max_depth", maxDepth);
        params.put("criterion", "variance_reduction");

        Map<String, Double> trainMetrics = evalRegression(model.predict(split.getXTrain()), split.getYTrain());
        Map<String, Double> valMetrics = evalRegression(model.predict(split.getXVal()), split.getYVal());
        Map<String, Double> testMetrics = evalRegression(model.predict(split.getXTest()), split.getYTest());

        return new ModelBenchmarkResult("Decision Tree Regressor", "DecisionTreeRegressor", params,
                trainMetrics, valMetrics, testMetrics, false, model);
    }

    // --- CLASSIFICATION MODELS ---

    private ModelBenchmarkResult trainLogisticRegression(DatasetSplit split, double lr, int epochs) {
        ModelBenchmarkResult base = trainLogisticRegressionCore(split, lr, epochs);
        return withEvaluation(split, base, s -> trainLogisticRegressionCore(s, lr, epochs).getTrainedModel());
    }

    private ModelBenchmarkResult trainLogisticRegressionCore(DatasetSplit split, double lr, int epochs) {
        double[][] X = split.getXTrain();
        double[] y = split.getYTrain();
        int n = X.length;
        int p = split.getFeatureNames().size();

        double[] weights = new double[p];
        double bias = 0.0;

        for (int ep = 0; ep < epochs; ep++) {
            double[] gradW = new double[p];
            double gradB = 0.0;

            for (int i = 0; i < n; i++) {
                double z = bias;
                for (int j = 0; j < p; j++) z += weights[j] * X[i][j];
                double pred = sigmoid(z);
                double err = pred - y[i];

                for (int j = 0; j < p; j++) gradW[j] += err * X[i][j];
                gradB += err;
            }

            for (int j = 0; j < p; j++) {
                weights[j] -= lr * (gradW[j] / n + 0.01 * weights[j]); // L2 regularization
            }
            bias -= lr * (gradB / n);
        }

        final double finalBias = bias;
        PredictiveModel model = new PredictiveModel() {
            @Override
            public double predictSingle(double[] x) {
                double z = finalBias;
                for (int j = 0; j < x.length; j++) z += weights[j] * x[j];
                return sigmoid(z) >= 0.5 ? 1.0 : 0.0;
            }
            @Override
            public double[] predict(double[][] X_in) {
                double[] preds = new double[X_in.length];
                for (int i = 0; i < X_in.length; i++) preds[i] = predictSingle(X_in[i]);
                return preds;
            }
            @Override
            public double[] getWeights() { return weights; }
            @Override
            public double predictProba(double[] x) {
                double z = finalBias;
                for (int j = 0; j < x.length; j++) z += weights[j] * x[j];
                return sigmoid(z);
            }
        };

        Map<String, Object> params = new HashMap<>();
        params.put("learning_rate", lr);
        params.put("epochs", epochs);
        params.put("l2_penalty", 0.01);

        Map<String, Double> trainMetrics = evalClassification(model.predict(split.getXTrain()), split.getYTrain());
        Map<String, Double> valMetrics = evalClassification(model.predict(split.getXVal()), split.getYVal());
        Map<String, Double> testMetrics = evalClassification(model.predict(split.getXTest()), split.getYTest());

        return new ModelBenchmarkResult("Logistic Regression (L2)", "LogisticRegression", params,
                trainMetrics, valMetrics, testMetrics, false, model);
    }

    private ModelBenchmarkResult trainDecisionTreeClassifier(DatasetSplit split, int maxDepth) {
        ModelBenchmarkResult base = trainDecisionTreeClassifierCore(split, maxDepth);
        return withEvaluation(split, base, s -> trainDecisionTreeClassifierCore(s, maxDepth).getTrainedModel());
    }

    private ModelBenchmarkResult trainDecisionTreeClassifierCore(DatasetSplit split, int maxDepth) {
        DecisionTreeNode root = buildClassificationTree(split.getXTrain(), split.getYTrain(), 0, maxDepth);

        PredictiveModel model = new PredictiveModel() {
            @Override
            public double predictSingle(double[] x) {
                return predictClassificationTree(root, x);
            }
            @Override
            public double[] predict(double[][] X_in) {
                double[] preds = new double[X_in.length];
                for (int i = 0; i < X_in.length; i++) preds[i] = predictSingle(X_in[i]);
                return preds;
            }
            @Override
            public double[] getWeights() { return new double[0]; }
            @Override
            public double predictProba(double[] x) {
                return predictClassificationTreeProb(root, x);
            }
        };

        Map<String, Object> params = new HashMap<>();
        params.put("max_depth", maxDepth);
        params.put("criterion", "gini_impurity");

        Map<String, Double> trainMetrics = evalClassification(model.predict(split.getXTrain()), split.getYTrain());
        Map<String, Double> valMetrics = evalClassification(model.predict(split.getXVal()), split.getYVal());
        Map<String, Double> testMetrics = evalClassification(model.predict(split.getXTest()), split.getYTest());

        return new ModelBenchmarkResult("Decision Tree Classifier", "DecisionTreeClassifier", params,
                trainMetrics, valMetrics, testMetrics, false, model);
    }

    private ModelBenchmarkResult trainRandomForestClassifier(DatasetSplit split, int numTrees, int maxDepth) {
        ModelBenchmarkResult base = trainRandomForestClassifierCore(split, numTrees, maxDepth);
        return withEvaluation(split, base, s -> trainRandomForestClassifierCore(s, numTrees, maxDepth).getTrainedModel());
    }

    private ModelBenchmarkResult trainRandomForestClassifierCore(DatasetSplit split, int numTrees, int maxDepth) {
        List<DecisionTreeNode> forest = new ArrayList<>();
        Random rand = new Random(42);
        int n = split.getXTrain().length;

        for (int t = 0; t < numTrees; t++) {
            // Bootstrap sample
            double[][] subX = new double[n][];
            double[] subY = new double[n];
            for (int i = 0; i < n; i++) {
                int pick = rand.nextInt(n);
                subX[i] = split.getXTrain()[pick];
                subY[i] = split.getYTrain()[pick];
            }
            forest.add(buildClassificationTree(subX, subY, 0, maxDepth));
        }

        PredictiveModel model = new PredictiveModel() {
            @Override
            public double predictSingle(double[] x) {
                int vote1 = 0;
                for (DecisionTreeNode tree : forest) {
                    if (predictClassificationTree(tree, x) >= 0.5) vote1++;
                }
                return (vote1 >= (forest.size() / 2.0)) ? 1.0 : 0.0;
            }
            @Override
            public double[] predict(double[][] X_in) {
                double[] preds = new double[X_in.length];
                for (int i = 0; i < X_in.length; i++) preds[i] = predictSingle(X_in[i]);
                return preds;
            }
            @Override
            public double[] getWeights() { return new double[0]; }
            @Override
            public double predictProba(double[] x) {
                // Mean leaf positive rate across trees: a graded score that stays
                // consistent with the majority vote at the 0.5 threshold.
                double sum = 0;
                for (DecisionTreeNode tree : forest) sum += predictClassificationTreeProb(tree, x);
                return sum / forest.size();
            }
        };

        Map<String, Object> params = new HashMap<>();
        params.put("n_estimators", numTrees);
        params.put("max_depth", maxDepth);

        Map<String, Double> trainMetrics = evalClassification(model.predict(split.getXTrain()), split.getYTrain());
        Map<String, Double> valMetrics = evalClassification(model.predict(split.getXVal()), split.getYVal());
        Map<String, Double> testMetrics = evalClassification(model.predict(split.getXTest()), split.getYTest());

        return new ModelBenchmarkResult(String.format("Random Forest (%d Trees)", numTrees), "RandomForestClassifier", params,
                trainMetrics, valMetrics, testMetrics, false, model);
    }

    // --- EVALUATION DETAIL (ROC/PR/confusion/CV) ---

    /**
     * Attaches the deep-evaluation payload (confusion matrix, ROC/PR curves, AUC,
     * per-class metrics and k-fold cross-validation) to a freshly trained model.
     */
    private ModelBenchmarkResult withEvaluation(DatasetSplit split, ModelBenchmarkResult base, ModelTrainer trainer) {
        Map<String, Object> evaluation = new LinkedHashMap<>();
        try {
            if ("CLASSIFICATION".equals(split.getProblemType())) {
                double[][] xTest = split.getXTest();
                double[] yTest = split.getYTest();
                double[] preds = base.getTrainedModel().predict(xTest);
                double[] scores = new double[xTest.length];
                for (int i = 0; i < xTest.length; i++) scores[i] = base.getTrainedModel().predictProba(xTest[i]);
                evaluation.putAll(classificationEvaluation(preds, scores, yTest, split.getClassMap()));
                evaluation.put("classDistribution", classDistribution(split));
            } else {
                double[] preds = base.getTrainedModel().predict(split.getXTest());
                evaluation.put("residualSummary", residualSummary(preds, split.getYTest()));
                evaluation.put("residualPoints", residualPoints(preds, split.getYTest()));
                evaluation.put("predictionPoints", predictionPoints(preds, split.getYTest()));
            }
            evaluation.put("crossValidation", crossValidate(split, trainer));
        } catch (Exception e) {
            evaluation.put("status", "UNAVAILABLE");
            evaluation.put("reason", e.getMessage() == null ? "evaluation failed" : e.getMessage());
        }
        return new ModelBenchmarkResult(
                base.getModelName(), base.getAlgorithm(), base.getHyperparameters(),
                base.getTrainMetrics(), base.getValMetrics(), base.getTestMetrics(),
                base.isBest(), base.getTrainedModel(), evaluation);
    }

    private Map<String, Object> classificationEvaluation(double[] preds, double[] scores,
                                                         double[] actuals, Map<String, Integer> classMap) {
        int n = Math.min(preds.length, actuals.length);
        Map<String, Object> out = new LinkedHashMap<>();
        if (n == 0) return out;

        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (int i = 0; i < n; i++) {
            boolean p = preds[i] >= 0.5;
            boolean a = actuals[i] >= 0.5;
            if (p && a) tp++;
            else if (p && !a) fp++;
            else if (!p && a) fn++;
            else tn++;
        }
        // rows are actual classes, columns predicted: [[TN, FP], [FN, TP]]
        out.put("confusionMatrix", Map.of(
                "rowLabels", Arrays.asList("actual_negative", "actual_positive"),
                "columnLabels", Arrays.asList("predicted_negative", "predicted_positive"),
                "rows", Arrays.asList(
                        Arrays.asList(tn, fp),
                        Arrays.asList(fn, tp)
                ),
                "trueNegatives", tn, "falsePositives", fp,
                "falseNegatives", fn, "truePositives", tp
        ));

        // Per-class precision/recall/F1/support
        double precisionPos = (tp + fp > 0) ? (double) tp / (tp + fp) : 0.0;
        double recallPos = (tp + fn > 0) ? (double) tp / (tp + fn) : 0.0;
        double precisionNeg = (tn + fn > 0) ? (double) tn / (tn + fn) : 0.0;
        double recallNeg = (tn + fp > 0) ? (double) tn / (tn + fp) : 0.0;
        double f1Pos = (precisionPos + recallPos > 0) ? 2 * precisionPos * recallPos / (precisionPos + recallPos) : 0.0;
        double f1Neg = (precisionNeg + recallNeg > 0) ? 2 * precisionNeg * recallNeg / (precisionNeg + recallNeg) : 0.0;

        String negLabel = labelFor(classMap, 0, "0");
        String posLabel = labelFor(classMap, 1, "1");
        List<Map<String, Object>> perClass = new ArrayList<>();
        perClass.add(Map.of("label", negLabel, "precision", round(precisionNeg, 4), "recall", round(recallNeg, 4),
                "f1", round(f1Neg, 4), "support", tn + fp));
        perClass.add(Map.of("label", posLabel, "precision", round(precisionPos, 4), "recall", round(recallPos, 4),
                "f1", round(f1Pos, 4), "support", tp + fn));
        double macroF1 = (f1Pos + f1Neg) / 2.0;
        out.put("perClass", perClass);
        out.put("macroF1", round(macroF1, 4));

        // ROC / PR from score curve when the model can produce real scores
        boolean hasScores = false;
        if (scores != null && scores.length >= n) {
            for (int i = 0; i < n; i++) {
                if (!Double.isNaN(scores[i])) { hasScores = true; break; }
            }
        }
        if (hasScores) {
            double[][] roc = rocCurve(scores, actuals, n);
            double auc = trapezoidArea(roc);
            out.put("rocCurve", downsample(roc));
            out.put("auc", round(auc, 4));

            double[][] pr = prCurve(scores, actuals, n);
            double ap = averagePrecision(pr);
            out.put("prCurve", downsample(pr));
            out.put("averagePrecision", round(ap, 4));

            out.put("calibration", calibration(scores, actuals, n));
        } else {
            out.put("rocStatus", "UNAVAILABLE_NO_SCORES");
        }
        return out;
    }

    /** Reliability-diagram bins + Brier score from real predicted probabilities. */
    private Map<String, Object> calibration(double[] scores, double[] actuals, int n) {
        int bins = 10;
        long[] binCount = new long[bins];
        double[] binSumPred = new double[bins];
        long[] binPos = new long[bins];
        double brier = 0.0;
        for (int i = 0; i < n; i++) {
            double s = Math.max(0.0, Math.min(1.0, scores[i]));
            double a = actuals[i] >= 0.5 ? 1.0 : 0.0;
            brier += (s - a) * (s - a);
            int b = Math.min(bins - 1, (int) (s * bins));
            binCount[b]++;
            binSumPred[b] += s;
            binPos[b] += (a >= 0.5) ? 1 : 0;
        }
        brier = n > 0 ? brier / n : 0.0;
        List<Map<String, Object>> points = new ArrayList<>();
        for (int b = 0; b < bins; b++) {
            if (binCount[b] == 0) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("meanPredicted", round(binSumPred[b] / binCount[b], 4));
            p.put("observedFrequency", round((double) binPos[b] / binCount[b], 4));
            p.put("count", binCount[b]);
            points.add(p);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("brierScore", round(brier, 4));
        out.put("bins", points);
        return out;
    }

    /** Downsampled [actual, residual] pairs for a residual scatter plot. */
    private double[][] residualPoints(double[] preds, double[] actuals) {
        int n = Math.min(preds.length, actuals.length);
        double[][] pts = new double[n][2];
        for (int i = 0; i < n; i++) {
            pts[i][0] = actuals[i];
            pts[i][1] = preds[i] - actuals[i];
        }
        return downsample(pts);
    }

    /** Downsampled [actual, predicted] pairs for an actual-vs-predicted plot. */
    private double[][] predictionPoints(double[] preds, double[] actuals) {
        int n = Math.min(preds.length, actuals.length);
        double[][] pts = new double[n][2];
        for (int i = 0; i < n; i++) {
            pts[i][0] = actuals[i];
            pts[i][1] = preds[i];
        }
        return downsample(pts);
    }

    /**
     * Class balance of every split, so the UI can show how the target is
     * distributed across train / validation / test.
     */
    private List<Map<String, Object>> classDistribution(DatasetSplit split) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Integer> classMap = split.getClassMap();
        Map<Integer, String> reverse = split.getReverseClassMap();
        if (classMap == null || classMap.isEmpty()) return out;

        Map<String, int[]> counts = new LinkedHashMap<>();
        for (String label : classMap.keySet()) {
            counts.put(label, new int[3]);
        }
        fillClassCounts(counts, reverse, split.getYTrain(), 0);
        fillClassCounts(counts, reverse, split.getYVal(), 1);
        fillClassCounts(counts, reverse, split.getYTest(), 2);

        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            int[] c = e.getValue();
            int total = c[0] + c[1] + c[2];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", e.getKey());
            row.put("train", c[0]);
            row.put("validation", c[1]);
            row.put("test", c[2]);
            row.put("total", total);
            row.put("trainProportion", total > 0 ? round((double) c[0] / total, 4) : 0.0);
            out.add(row);
        }
        return out;
    }

    private void fillClassCounts(Map<String, int[]> counts, Map<Integer, String> reverse, double[] y, int splitIndex) {
        if (y == null || reverse == null) return;
        for (double v : y) {
            String label = reverse.get((int) Math.round(v));
            if (label == null) continue;
            int[] c = counts.get(label);
            if (c != null) c[splitIndex]++;
        }
    }

    private String labelFor(Map<String, Integer> classMap, int idx, String fallback) {
        if (classMap != null) {
            for (Map.Entry<String, Integer> e : classMap.entrySet()) {
                if (e.getValue() != null && e.getValue() == idx) return e.getKey();
            }
        }
        return fallback;
    }

    /** ROC points [fpr, tpr] sweeping the score threshold from high to low. */
    private double[][] rocCurve(double[] scores, double[] actuals, int n) {
        double[][] pairs = new double[n][2];
        for (int i = 0; i < n; i++) pairs[i][0] = scores[i];
        for (int i = 0; i < n; i++) pairs[i][1] = actuals[i] >= 0.5 ? 1 : 0;
        Arrays.sort(pairs, (a, b) -> Double.compare(b[0], a[0]));

        int positives = 0, negatives = 0;
        for (int i = 0; i < n; i++) {
            if (pairs[i][1] >= 0.5) positives++; else negatives++;
        }
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{0.0, 0.0});
        if (positives == 0 || negatives == 0) {
            points.add(new double[]{1.0, 1.0});
            return points.toArray(new double[0][]);
        }
        int tp = 0, fp = 0;
        for (int i = 0; i < n; i++) {
            if (pairs[i][1] >= 0.5) tp++; else fp++;
            double tpr = (double) tp / positives;
            double fpr = (double) fp / negatives;
            double[] last = points.get(points.size() - 1);
            if (Math.abs(last[0] - fpr) > 1e-9 || Math.abs(last[1] - tpr) > 1e-9) {
                points.add(new double[]{fpr, tpr});
            }
        }
        return points.toArray(new double[0][]);
    }

    /** PR points [recall, precision] sweeping the score threshold from high to low. */
    private double[][] prCurve(double[] scores, double[] actuals, int n) {
        double[][] pairs = new double[n][2];
        for (int i = 0; i < n; i++) pairs[i][0] = scores[i];
        for (int i = 0; i < n; i++) pairs[i][1] = actuals[i] >= 0.5 ? 1 : 0;
        Arrays.sort(pairs, (a, b) -> Double.compare(b[0], a[0]));

        int positives = 0;
        for (int i = 0; i < n; i++) if (pairs[i][1] >= 0.5) positives++;
        List<double[]> points = new ArrayList<>();
        if (positives == 0) {
            points.add(new double[]{0.0, 1.0});
            return points.toArray(new double[0][]);
        }
        int tp = 0, fp = 0;
        points.add(new double[]{0.0, 1.0});
        for (int i = 0; i < n; i++) {
            if (pairs[i][1] >= 0.5) tp++; else fp++;
            double recall = (double) tp / positives;
            double precision = (double) tp / (tp + fp);
            points.add(new double[]{recall, precision});
        }
        return points.toArray(new double[0][]);
    }

    private double trapezoidArea(double[][] curve) {
        if (curve == null || curve.length < 2) return 0.0;
        double area = 0;
        for (int i = 1; i < curve.length; i++) {
            double dx = curve[i][0] - curve[i - 1][0];
            double avgY = (curve[i][1] + curve[i - 1][1]) / 2.0;
            area += dx * avgY;
        }
        return Math.max(0.0, Math.min(1.0, area));
    }

    private double averagePrecision(double[][] pr) {
        if (pr == null || pr.length < 2) return 0.0;
        double ap = 0;
        for (int i = 1; i < pr.length; i++) {
            double dRecall = pr[i][0] - pr[i - 1][0];
            ap += dRecall * pr[i][1];
        }
        return Math.max(0.0, Math.min(1.0, ap));
    }

    private double[][] downsample(double[][] points) {
        if (points == null || points.length <= MAX_CURVE_POINTS) return points;
        double[][] out = new double[MAX_CURVE_POINTS][];
        double step = (double) (points.length - 1) / (MAX_CURVE_POINTS - 1);
        for (int i = 0; i < MAX_CURVE_POINTS; i++) {
            out[i] = points[(int) Math.round(i * step)];
        }
        return out;
    }

    private Map<String, Object> residualSummary(double[] preds, double[] actuals) {
        int n = Math.min(preds.length, actuals.length);
        Map<String, Object> out = new LinkedHashMap<>();
        if (n == 0) return out;
        double sumErr = 0, absSum = 0, maxAbs = 0;
        for (int i = 0; i < n; i++) {
            double err = preds[i] - actuals[i];
            sumErr += err;
            absSum += Math.abs(err);
            maxAbs = Math.max(maxAbs, Math.abs(err));
        }
        out.put("meanError", round(sumErr / n, 4));
        out.put("mae", round(absSum / n, 4));
        out.put("maxAbsError", round(maxAbs, 4));
        out.put("n", n);
        return out;
    }

    /**
     * k-fold cross-validation on the training fold. Skipped (with an explicit
     * reason) when the training set is too small or too large to retrain affordably.
     */
    private Map<String, Object> crossValidate(DatasetSplit split, ModelTrainer trainer) {
        Map<String, Object> out = new LinkedHashMap<>();
        int n = split.getXTrain().length;
        if (n < CV_MIN_ROWS) {
            out.put("status", "SKIPPED");
            out.put("reason", "fewer than " + CV_MIN_ROWS + " training rows");
            return out;
        }
        if (n > CV_MAX_ROWS) {
            out.put("status", "SKIPPED");
            out.put("reason", "cross-validation retraining limited to " + CV_MAX_ROWS + " rows");
            return out;
        }
        boolean classification = "CLASSIFICATION".equals(split.getProblemType());
        int folds = n >= 50 ? 5 : 3;

        Random rand = new Random(42);
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        List<Integer> idx = Arrays.asList(order);
        Collections.shuffle(idx, rand);

        List<Double> scores = new ArrayList<>();
        List<Double> trainScores = new ArrayList<>();
        int foldSize = n / folds;
        for (int f = 0; f < folds; f++) {
            int from = f * foldSize;
            int to = (f == folds - 1) ? n : from + foldSize;
            List<double[]> valX = new ArrayList<>();
            List<Double> valY = new ArrayList<>();
            List<double[]> trainX = new ArrayList<>();
            List<Double> trainY = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int gi = idx.get(i);
                if (i >= from && i < to) {
                    valX.add(split.getXTrain()[gi]);
                    valY.add(split.getYTrain()[gi]);
                } else {
                    trainX.add(split.getXTrain()[gi]);
                    trainY.add(split.getYTrain()[gi]);
                }
            }
            if (valX.isEmpty() || trainX.isEmpty()) continue;

            DatasetSplit foldSplit = new DatasetSplit(
                    trainX.toArray(new double[0][]), toArray(trainY),
                    valX.toArray(new double[0][]), toArray(valY),
                    new double[0][], new double[0],
                    split.getFeatureNames(), split.getTargetColumn(), split.getProblemType(),
                    split.getClassMap(), split.getReverseClassMap(), split.getPreprocessingConfig());

            try {
                PredictiveModel m = trainer.train(foldSplit);
                double[] preds = m.predict(foldSplit.getXVal());
                double[] actuals = foldSplit.getYVal();
                if (classification) {
                    Map<String, Double> cm = evalClassification(preds, actuals);
                    scores.add(cm.getOrDefault("f1", 0.0));
                    Map<String, Double> tm = evalClassification(m.predict(foldSplit.getXTrain()), foldSplit.getYTrain());
                    trainScores.add(tm.getOrDefault("f1", 0.0));
                } else {
                    Map<String, Double> rm = evalRegression(preds, actuals);
                    scores.add(rm.getOrDefault("r2", 0.0));
                    Map<String, Double> tm = evalRegression(m.predict(foldSplit.getXTrain()), foldSplit.getYTrain());
                    trainScores.add(tm.getOrDefault("r2", 0.0));
                }
            } catch (Exception e) {
                // A failed fold aborts CV honestly rather than silently shrinking k.
                out.put("status", "FAILED");
                out.put("reason", e.getMessage() == null ? "fold training failed" : e.getMessage());
                return out;
            }
        }

        if (scores.isEmpty()) {
            out.put("status", "SKIPPED");
            out.put("reason", "no usable folds");
            return out;
        }
        double mean = 0;
        for (double s : scores) mean += s;
        mean /= scores.size();
        double var = 0;
        for (double s : scores) var += Math.pow(s - mean, 2);
        double std = scores.size() > 1 ? Math.sqrt(var / (scores.size() - 1)) : 0.0;

        out.put("status", "COMPLETED");
        out.put("folds", scores.size());
        out.put("metric", classification ? "f1" : "r2");
        out.put("scores", scores.stream().map(s -> round(s, 4)).toList());
        out.put("mean", round(mean, 4));
        out.put("std", round(std, 4));
        out.put("validationScores", scores.stream().map(s -> round(s, 4)).toList());
        out.put("trainScores", trainScores.stream().map(s -> round(s, 4)).toList());
        out.put("trainMean", trainScores.isEmpty() ? null
                : round(trainScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0), 4));
        return out;
    }

    // --- DECISION TREE DATA STRUCTURE & LOGIC ---

    private static class DecisionTreeNode {
        boolean isLeaf;
        double value;
        double prob; // classification leaves: share of positive rows in the node
        int featureIndex;
        double threshold;
        DecisionTreeNode left;
        DecisionTreeNode right;
    }

    private DecisionTreeNode buildRegressionTree(double[][] X, double[] y, int depth, int maxDepth) {
        DecisionTreeNode node = new DecisionTreeNode();
        if (X.length == 0) return node;

        double mean = 0;
        for (double v : y) mean += v;
        mean /= y.length;

        if (depth >= maxDepth || X.length <= 3) {
            node.isLeaf = true;
            node.value = mean;
            return node;
        }

        int bestFeat = -1;
        double bestThresh = 0;
        double bestVarRed = -1;

        int p = X[0].length;
        double currentVar = calcVariance(y);

        for (int j = 0; j < p; j++) {
            for (int i = 0; i < X.length; i++) {
                double thresh = X[i][j];
                List<Double> leftY = new ArrayList<>();
                List<Double> rightY = new ArrayList<>();

                for (int k = 0; k < X.length; k++) {
                    if (X[k][j] <= thresh) leftY.add(y[k]);
                    else rightY.add(y[k]);
                }

                if (leftY.size() < 1 || rightY.size() < 1) continue;

                double varL = calcVarianceList(leftY);
                double varR = calcVarianceList(rightY);
                double weightedVar = ((double) leftY.size() / y.length * varL) + ((double) rightY.size() / y.length * varR);
                double varRed = currentVar - weightedVar;

                if (varRed > bestVarRed) {
                    bestVarRed = varRed;
                    bestFeat = j;
                    bestThresh = thresh;
                }
            }
        }

        if (bestFeat == -1 || bestVarRed <= 0) {
            node.isLeaf = true;
            node.value = mean;
            return node;
        }

        node.featureIndex = bestFeat;
        node.threshold = bestThresh;

        List<double[]> leftX = new ArrayList<>();
        List<Double> leftY = new ArrayList<>();
        List<double[]> rightX = new ArrayList<>();
        List<Double> rightY = new ArrayList<>();

        for (int i = 0; i < X.length; i++) {
            if (X[i][bestFeat] <= bestThresh) {
                leftX.add(X[i]);
                leftY.add(y[i]);
            } else {
                rightX.add(X[i]);
                rightY.add(y[i]);
            }
        }

        node.left = buildRegressionTree(leftX.toArray(new double[0][]), toArray(leftY), depth + 1, maxDepth);
        node.right = buildRegressionTree(rightX.toArray(new double[0][]), toArray(rightY), depth + 1, maxDepth);
        return node;
    }

    private DecisionTreeNode buildClassificationTree(double[][] X, double[] y, int depth, int maxDepth) {
        DecisionTreeNode node = new DecisionTreeNode();
        if (X.length == 0) return node;

        int count1 = 0;
        for (double v : y) if (v >= 0.5) count1++;
        double majority = (count1 >= (y.length / 2.0)) ? 1.0 : 0.0;
        double positiveRate = y.length > 0 ? (double) count1 / y.length : 0.0;

        if (depth >= maxDepth || X.length <= 3 || count1 == 0 || count1 == y.length) {
            node.isLeaf = true;
            node.value = majority;
            node.prob = positiveRate;
            return node;
        }

        int bestFeat = -1;
        double bestThresh = 0;
        double bestGiniGain = -1;
        double currentGini = calcGini(y);

        int p = X[0].length;
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < X.length; i++) {
                double thresh = X[i][j];
                List<Double> leftY = new ArrayList<>();
                List<Double> rightY = new ArrayList<>();

                for (int k = 0; k < X.length; k++) {
                    if (X[k][j] <= thresh) leftY.add(y[k]);
                    else rightY.add(y[k]);
                }

                if (leftY.size() < 1 || rightY.size() < 1) continue;

                double giniL = calcGiniList(leftY);
                double giniR = calcGiniList(rightY);
                double weightedGini = ((double) leftY.size() / y.length * giniL) + ((double) rightY.size() / y.length * giniR);
                double gain = currentGini - weightedGini;

                if (gain > bestGiniGain) {
                    bestGiniGain = gain;
                    bestFeat = j;
                    bestThresh = thresh;
                }
            }
        }

        if (bestFeat == -1 || bestGiniGain <= 0) {
            node.isLeaf = true;
            node.value = majority;
            node.prob = positiveRate;
            return node;
        }

        node.featureIndex = bestFeat;
        node.threshold = bestThresh;

        List<double[]> leftX = new ArrayList<>();
        List<Double> leftY = new ArrayList<>();
        List<double[]> rightX = new ArrayList<>();
        List<Double> rightY = new ArrayList<>();

        for (int i = 0; i < X.length; i++) {
            if (X[i][bestFeat] <= bestThresh) {
                leftX.add(X[i]);
                leftY.add(y[i]);
            } else {
                rightX.add(X[i]);
                rightY.add(y[i]);
            }
        }

        node.left = buildClassificationTree(leftX.toArray(new double[0][]), toArray(leftY), depth + 1, maxDepth);
        node.right = buildClassificationTree(rightX.toArray(new double[0][]), toArray(rightY), depth + 1, maxDepth);
        return node;
    }

    private double predictRegressionTree(DecisionTreeNode node, double[] x) {
        if (node == null) return 0.0;
        if (node.isLeaf) return node.value;
        if (x[node.featureIndex] <= node.threshold) return predictRegressionTree(node.left, x);
        return predictRegressionTree(node.right, x);
    }

    private double predictClassificationTree(DecisionTreeNode node, double[] x) {
        if (node == null) return 0.0;
        if (node.isLeaf) return node.value;
        if (x[node.featureIndex] <= node.threshold) return predictClassificationTree(node.left, x);
        return predictClassificationTree(node.right, x);
    }

    private double predictClassificationTreeProb(DecisionTreeNode node, double[] x) {
        if (node == null) return Double.NaN;
        if (node.isLeaf) return node.prob;
        if (x[node.featureIndex] <= node.threshold) return predictClassificationTreeProb(node.left, x);
        return predictClassificationTreeProb(node.right, x);
    }

    // --- EVALUATION METRICS ---

    private Map<String, Double> evalRegression(double[] preds, double[] actuals) {
        int n = Math.min(preds.length, actuals.length);
        if (n == 0) return Collections.emptyMap();

        double sse = 0;
        double sae = 0;
        double actualSum = 0;

        for (int i = 0; i < n; i++) {
            double err = preds[i] - actuals[i];
            sse += err * err;
            sae += Math.abs(err);
            actualSum += actuals[i];
        }

        double mse = sse / n;
        double rmse = Math.sqrt(mse);
        double mae = sae / n;
        double meanY = actualSum / n;

        double sst = 0;
        for (int i = 0; i < n; i++) {
            sst += Math.pow(actuals[i] - meanY, 2);
        }

        double r2 = (sst > 0) ? 1.0 - (sse / sst) : 0.0;

        Map<String, Double> map = new LinkedHashMap<>();
        map.put("r2", round(r2, 4));
        map.put("rmse", round(rmse, 4));
        map.put("mae", round(mae, 4));
        map.put("mse", round(mse, 4));
        return map;
    }

    private Map<String, Double> evalClassification(double[] preds, double[] actuals) {
        int n = Math.min(preds.length, actuals.length);
        if (n == 0) return Collections.emptyMap();

        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (int i = 0; i < n; i++) {
            boolean p = preds[i] >= 0.5;
            boolean a = actuals[i] >= 0.5;
            if (p && a) tp++;
            else if (p && !a) fp++;
            else if (!p && a) fn++;
            else tn++;
        }

        double accuracy = (double) (tp + tn) / n;
        double precision = (tp + fp > 0) ? (double) tp / (tp + fp) : 0.0;
        double recall = (tp + fn > 0) ? (double) tp / (tp + fn) : 0.0;
        double f1 = (precision + recall > 0) ? 2.0 * (precision * recall) / (precision + recall) : 0.0;

        Map<String, Double> map = new LinkedHashMap<>();
        map.put("accuracy", round(accuracy, 4));
        map.put("precision", round(precision, 4));
        map.put("recall", round(recall, 4));
        map.put("f1", round(f1, 4));
        map.put("true_positives", (double) tp);
        map.put("false_positives", (double) fp);
        map.put("false_negatives", (double) fn);
        map.put("true_negatives", (double) tn);
        return map;
    }

    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-Math.max(-20.0, Math.min(20.0, z))));
    }

    private double calcVariance(double[] y) {
        if (y.length < 2) return 0.0;
        double mean = 0;
        for (double v : y) mean += v;
        mean /= y.length;
        double ss = 0;
        for (double v : y) ss += Math.pow(v - mean, 2);
        return ss / (y.length - 1);
    }

    private double calcVarianceList(List<Double> y) {
        if (y.size() < 2) return 0.0;
        double mean = 0;
        for (double v : y) mean += v;
        mean /= y.size();
        double ss = 0;
        for (double v : y) ss += Math.pow(v - mean, 2);
        return ss / (y.size() - 1);
    }

    private double calcGini(double[] y) {
        if (y.length == 0) return 0.0;
        int count1 = 0;
        for (double v : y) if (v >= 0.5) count1++;
        double p1 = (double) count1 / y.length;
        double p0 = 1.0 - p1;
        return 1.0 - (p1 * p1 + p0 * p0);
    }

    private double calcGiniList(List<Double> y) {
        if (y.isEmpty()) return 0.0;
        int count1 = 0;
        for (double v : y) if (v >= 0.5) count1++;
        double p1 = (double) count1 / y.size();
        double p0 = 1.0 - p1;
        return 1.0 - (p1 * p1 + p0 * p0);
    }

    private double[] toArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private double round(double val, int places) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return 0.0;
        double factor = Math.pow(10, places);
        return Math.round(val * factor) / factor;
    }
}
