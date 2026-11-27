package com.autoscientist.analysis.xai;

import com.autoscientist.analysis.feature.FeatureEngineeringEngine.DatasetSplit;
import com.autoscientist.analysis.ml.AutoMlEngine.ModelBenchmarkResult;
import com.autoscientist.analysis.ml.AutoMlEngine.PredictiveModel;
import com.autoscientist.dto.ml.FeatureImportanceDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExplainabilityEngine {

    public List<FeatureImportanceDto> computeFeatureImportance(DatasetSplit split, ModelBenchmarkResult modelResult) {
        PredictiveModel model = modelResult.getTrainedModel();
        double[][] xVal = split.getXVal();
        double[] yVal = split.getYVal();
        List<String> featureNames = split.getFeatureNames();
        int p = featureNames.size();
        String problemType = split.getProblemType();

        if (xVal == null || xVal.length == 0 || p == 0) {
            return Collections.emptyList();
        }

        // 1. Calculate baseline metric on validation set
        double baselineScore = evaluateMetric(model, xVal, yVal, problemType);

        // 2. Permutation Feature Importance
        double[] importanceScores = new double[p];
        Random rng = new Random(42);

        for (int j = 0; j < p; j++) {
            double[] original = new double[xVal.length];
            for (int i = 0; i < xVal.length; i++) {
                original[i] = xVal[i][j];
            }
            shuffleColumn(xVal, j, rng);
            double permutedScore = evaluateMetric(model, xVal, yVal, problemType);
            for (int i = 0; i < xVal.length; i++) {
                xVal[i][j] = original[i];
            }
            importanceScores[j] = Math.max(0.0, baselineScore - permutedScore);
        }

        // Sum of importance for relative percentage calculation
        double totalImportance = 0.0;
        for (double s : importanceScores) {
            totalImportance += s;
        }

        double[] weights = model.getWeights();

        List<FeatureImportanceDto> list = new ArrayList<>();
        for (int j = 0; j < p; j++) {
            String name = featureNames.get(j);
            double rawScore = importanceScores[j];
            double relPct = (totalImportance > 0.0) ? (rawScore / totalImportance) * 100.0 : (100.0 / p);

            String direction = "NON_LINEAR";
            if (weights != null && weights.length > j) {
                if (weights[j] > 0.001) {
                    direction = "POSITIVE";
                } else if (weights[j] < -0.001) {
                    direction = "NEGATIVE";
                }
            }

            list.add(FeatureImportanceDto.builder()
                    .featureName(name)
                    .importanceScore(Math.round(rawScore * 10000.0) / 10000.0)
                    .relativePercentage(Math.round(relPct * 10.0) / 10.0)
                    .direction(direction)
                    .build());
        }

        // Sort descending by importanceScore
        list.sort((a, b) -> Double.compare(b.getImportanceScore(), a.getImportanceScore()));

        // Assign ranks and generate scientific interpretation
        for (int rank = 0; rank < list.size(); rank++) {
            FeatureImportanceDto dto = list.get(rank);
            dto.setRank(rank + 1);

            String dirText = dto.getDirection().equals("POSITIVE") ? "positive association"
                    : dto.getDirection().equals("NEGATIVE") ? "inverse (negative) relationship"
                    : "complex non-linear influence";

            String interp = String.format("Feature '%s' ranks #%d in predictive weight (%.1f%% relative influence), exhibiting a %s with '%s'.",
                    dto.getFeatureName(), rank + 1, dto.getRelativePercentage(), dirText, split.getTargetColumn());
            dto.setInterpretation(interp);
        }

        return list;
    }

    private double evaluateMetric(PredictiveModel model, double[][] X, double[] y, String problemType) {
        double[] preds = model.predict(X);
        if ("REGRESSION".equals(problemType)) {
            // Use R-squared; if invalid, fallback to negative MSE
            double meanY = 0;
            for (double v : y) meanY += v;
            meanY /= y.length;

            double ssTot = 0, ssRes = 0;
            for (int i = 0; i < y.length; i++) {
                ssTot += Math.pow(y[i] - meanY, 2);
                ssRes += Math.pow(y[i] - preds[i], 2);
            }
            if (ssTot > 1e-9) {
                return 1.0 - (ssRes / ssTot);
            } else {
                return -ssRes / y.length;
            }
        } else {
            // Accuracy / Micro-F1 for classification
            int correct = 0;
            for (int i = 0; i < y.length; i++) {
                if (Math.round(preds[i]) == Math.round(y[i])) {
                    correct++;
                }
            }
            return (double) correct / y.length;
        }
    }

    private void shuffleColumn(double[][] matrix, int colIndex, Random rng) {
        int n = matrix.length;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            double temp = matrix[i][colIndex];
            matrix[i][colIndex] = matrix[j][colIndex];
            matrix[j][colIndex] = temp;
        }
    }
}
