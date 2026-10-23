package com.autoscientist.analysis.fairness;

import com.autoscientist.analysis.feature.FeatureEngineeringEngine.DatasetSplit;
import com.autoscientist.analysis.ml.AutoMlEngine.ModelBenchmarkResult;
import com.autoscientist.analysis.ml.AutoMlEngine.PredictiveModel;
import com.autoscientist.dto.ml.FairnessDto;
import com.autoscientist.entity.DatasetColumn;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FairnessEngine {

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "gender", "sex", "race", "ethnicity", "age", "marital", "region", "disability", "minority"
    );

    public FairnessDto evaluateFairness(List<DatasetColumn> columns, DatasetSplit split,
                                        ModelBenchmarkResult bestModel, String requestedSensitiveCol) {
        // 1. Identify sensitive column
        String sensitiveCol = requestedSensitiveCol;
        if (sensitiveCol == null || sensitiveCol.isBlank()) {
            for (DatasetColumn col : columns) {
                String lower = col.getColumnName().toLowerCase();
                for (String kw : SENSITIVE_KEYWORDS) {
                    if (lower.contains(kw) && !col.getColumnName().equalsIgnoreCase(split.getTargetColumn())) {
                        sensitiveCol = col.getColumnName();
                        break;
                    }
                }
                if (sensitiveCol != null) break;
            }
        }

        if (sensitiveCol == null) {
            return FairnessDto.builder()
                    .sensitiveColumn("None Identified")
                    .demographicParityRatio(1.0)
                    .disparateImpactStatus("NOT_APPLICABLE")
                    .groupPositiveRates(Collections.emptyMap())
                    .groupErrorRates(Collections.emptyMap())
                    .recommendation("No protected or sensitive attributes (e.g., gender, race, age) were detected. Algorithmic fairness checks are nominal.")
                    .build();
        }

        // Check if sensitive column is one of the features (or one-hot encoded features)
        PredictiveModel model = bestModel.getTrainedModel();
        double[][] xVal = split.getXVal();
        double[] yVal = split.getYVal();
        List<String> featureNames = split.getFeatureNames();

        // Find indices of features matching the sensitive column
        List<Integer> sensitiveIndices = new ArrayList<>();
        for (int i = 0; i < featureNames.size(); i++) {
            if (featureNames.get(i).equalsIgnoreCase(sensitiveCol) ||
                    featureNames.get(i).toLowerCase().startsWith(sensitiveCol.toLowerCase() + "_")) {
                sensitiveIndices.add(i);
            }
        }

        if (sensitiveIndices.isEmpty() || xVal == null || xVal.length == 0) {
            return FairnessDto.builder()
                    .sensitiveColumn(sensitiveCol)
                    .demographicParityRatio(1.0)
                    .disparateImpactStatus("NOT_APPLICABLE")
                    .groupPositiveRates(Collections.emptyMap())
                    .groupErrorRates(Collections.emptyMap())
                    .recommendation("Sensitive attribute '" + sensitiveCol + "' was excluded from feature matrix. Direct bias is mitigated.")
                    .build();
        }

        // Map validation samples to group labels
        Map<String, List<Integer>> groupSampleIndices = new HashMap<>();
        for (int i = 0; i < xVal.length; i++) {
            String groupKey = "Other";
            for (int idx : sensitiveIndices) {
                if (xVal[i][idx] > 0.5) { // Active one-hot category or high value
                    groupKey = featureNames.get(idx).replace(sensitiveCol + "_", "");
                    break;
                }
            }
            groupSampleIndices.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(i);
        }

        if (groupSampleIndices.size() < 2) {
            // Only 1 group present in validation slice
            return FairnessDto.builder()
                    .sensitiveColumn(sensitiveCol)
                    .demographicParityRatio(1.0)
                    .disparateImpactStatus("PASS")
                    .groupPositiveRates(Collections.emptyMap())
                    .groupErrorRates(Collections.emptyMap())
                    .recommendation("Homogeneous validation group observed for '" + sensitiveCol + "'. No immediate disparity detected.")
                    .build();
        }

        double[] preds = model.predict(xVal);
        Map<String, Double> groupPositiveRates = new HashMap<>();
        Map<String, Double> groupErrorRates = new HashMap<>();
        Map<String, Integer> groupSampleSizes = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : groupSampleIndices.entrySet()) {
            groupSampleSizes.put(entry.getKey(), entry.getValue().size());
        }

        if ("CLASSIFICATION".equals(split.getProblemType())) {
            for (Map.Entry<String, List<Integer>> entry : groupSampleIndices.entrySet()) {
                String group = entry.getKey();
                List<Integer> indices = entry.getValue();
                int positiveCount = 0;
                int errors = 0;

                for (int idx : indices) {
                    if (Math.round(preds[idx]) == 1) positiveCount++;
                    if (Math.round(preds[idx]) != Math.round(yVal[idx])) errors++;
                }

                double posRate = (double) positiveCount / indices.size();
                double errRate = (double) errors / indices.size();
                groupPositiveRates.put(group, Math.round(posRate * 1000.0) / 1000.0);
                groupErrorRates.put(group, Math.round(errRate * 1000.0) / 1000.0);
            }
        } else {
            // Regression: compare mean predicted outcome and mean absolute error
            for (Map.Entry<String, List<Integer>> entry : groupSampleIndices.entrySet()) {
                String group = entry.getKey();
                List<Integer> indices = entry.getValue();
                double sumPred = 0;
                double sumAbsErr = 0;

                for (int idx : indices) {
                    sumPred += preds[idx];
                    sumAbsErr += Math.abs(preds[idx] - yVal[idx]);
                }

                double meanPred = sumPred / indices.size();
                double mae = sumAbsErr / indices.size();
                groupPositiveRates.put(group, Math.round(meanPred * 100.0) / 100.0);
                groupErrorRates.put(group, Math.round(mae * 100.0) / 100.0);
            }
        }

        // Compute Demographic Parity Ratio (min rate / max rate)
        double minRate = groupPositiveRates.values().stream().min(Double::compare).orElse(1.0);
        double maxRate = groupPositiveRates.values().stream().max(Double::compare).orElse(1.0);
        double disparityRatio = (maxRate > 1e-6) ? (minRate / maxRate) : 1.0;
        disparityRatio = Math.round(disparityRatio * 1000.0) / 1000.0;

        String status = disparityRatio >= 0.80 ? "PASS" : "FLAGGED";
        String recommendation = status.equals("PASS")
                ? String.format("Satisfies the EEOC 80%% Four-Fifths rule across '%s' subgroups (disparity ratio = %.2f). Disparate impact is minimal.",
                sensitiveCol, disparityRatio)
                : String.format("Disparity ratio of %.2f across '%s' violates the Four-Fifths rule (< 0.80). Check for proxy variables or historical sampling bias.",
                disparityRatio, sensitiveCol);

        return FairnessDto.builder()
                .sensitiveColumn(sensitiveCol)
                .demographicParityRatio(disparityRatio)
                .disparateImpactStatus(status)
                .groupPositiveRates(groupPositiveRates)
                .groupErrorRates(groupErrorRates)
                .groupSampleSizes(groupSampleSizes)
                .recommendation(recommendation)
                .build();
    }
}
