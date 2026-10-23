package com.autoscientist.analysis.findings;

import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.entity.Hypothesis;
import com.autoscientist.entity.ResearchQuestion;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class QuestionAnalysisEngine {

    public static class QuestionAnalysisResult {
        private final String predictorVariable;
        private final String targetVariable;
        private final String questionType;
        private final String suggestedTest;
        private final List<Hypothesis> hypotheses;

        public QuestionAnalysisResult(String predictorVariable, String targetVariable,
                                      String questionType, String suggestedTest,
                                      List<Hypothesis> hypotheses) {
            this.predictorVariable = predictorVariable;
            this.targetVariable = targetVariable;
            this.questionType = questionType;
            this.suggestedTest = suggestedTest;
            this.hypotheses = hypotheses;
        }

        public String getPredictorVariable() { return predictorVariable; }
        public String getTargetVariable() { return targetVariable; }
        public String getQuestionType() { return questionType; }
        public String getSuggestedTest() { return suggestedTest; }
        public List<Hypothesis> getHypotheses() { return hypotheses; }
    }

    public QuestionAnalysisResult analyzeQuestion(String questionText, String explicitPredictor,
                                                  String explicitTarget, List<DatasetColumn> columns,
                                                  ResearchQuestion researchQuestion) {

        String predictor = explicitPredictor;
        String target = explicitTarget;

        Map<String, DatasetColumn> columnMap = new HashMap<>();
        for (DatasetColumn col : columns) {
            columnMap.put(col.getColumnName().toLowerCase(), col);
            columnMap.put(col.getColumnName().toLowerCase().replace("_", " "), col);
        }

        // If not explicitly provided, extract from question text.
        // Identifier columns are never chosen implicitly — they carry no analytic meaning,
        // so auto-detection works only on non-identifier variables.
        if (predictor == null || target == null) {
            String lowerText = questionText.toLowerCase();
            List<String> matchedColumns = new ArrayList<>();

            for (DatasetColumn col : columns) {
                if ("IDENTIFIER".equals(col.getDataType())) continue;
                String cName = col.getColumnName().toLowerCase();
                String cNameClean = cName.replace("_", " ");
                if (lowerText.contains(cName) || lowerText.contains(cNameClean)) {
                    if (!matchedColumns.contains(col.getColumnName())) {
                        matchedColumns.add(col.getColumnName());
                    }
                }
            }

            if (matchedColumns.size() >= 2) {
                if (predictor == null) predictor = matchedColumns.get(0);
                if (target == null) target = matchedColumns.get(1);
            } else if (matchedColumns.size() == 1) {
                if (target == null) target = matchedColumns.get(0);
                // Choose first informative non-ID column as predictor
                for (DatasetColumn c : columns) {
                    if (!c.getColumnName().equals(target) && !"IDENTIFIER".equals(c.getDataType())
                            && c.getUniqueCount() > 1 && c.getUniqueCount() < columns.size()) {
                        predictor = c.getColumnName();
                        break;
                    }
                }
            } else {
                // Fallback: pick first two non-constant, non-identifier columns
                List<DatasetColumn> validCols = new ArrayList<>();
                for (DatasetColumn c : columns) {
                    if (c.getUniqueCount() > 1 && !c.getDataType().equals("TEXT") && !c.getDataType().equals("IDENTIFIER")) {
                        validCols.add(c);
                    }
                }
                if (validCols.size() >= 2) {
                    predictor = validCols.get(0).getColumnName();
                    target = validCols.get(1).getColumnName();
                } else {
                    // Not enough analysable variables: leave unresolved rather than
                    // silently testing an identifier or a constant column. The caller
                    // surfaces this as a validation error.
                    predictor = null;
                    target = null;
                }
            }
        }

        if (predictor == null || target == null || predictor.isBlank() || target.isBlank()) {
            throw new IllegalArgumentException(
                    "Could not determine the variables for this question. Select a predictor and a target explicitly "
                    + "(at least two non-identifier, non-constant variables are required).");
        }

        // Determine variable types
        DatasetColumn colX = columnMap.get(predictor.toLowerCase().replace("_", " "));
        if (colX == null) colX = columnMap.get(predictor.toLowerCase());
        DatasetColumn colY = columnMap.get(target.toLowerCase().replace("_", " "));
        if (colY == null) colY = columnMap.get(target.toLowerCase());

        String typeX = colX != null ? colX.getDataType() : "NUMERIC";
        String typeY = colY != null ? colY.getDataType() : "NUMERIC";

        String suggestedTest = "PEARSON";
        String questionType = "ASSOCIATION";

        if (typeX.equals("NUMERIC") && typeY.equals("NUMERIC")) {
            suggestedTest = "PEARSON";
            questionType = "ASSOCIATION";
        } else if (typeX.equals("NUMERIC") && (typeY.equals("CATEGORICAL") || typeY.equals("BOOLEAN"))) {
            int uniqueY = colY != null ? colY.getUniqueCount() : 2;
            suggestedTest = (uniqueY <= 2) ? "WELCH_T_TEST" : "ONE_WAY_ANOVA";
            questionType = "DIFFERENCE";
        } else if ((typeX.equals("CATEGORICAL") || typeX.equals("BOOLEAN")) && typeY.equals("NUMERIC")) {
            int uniqueX = colX != null ? colX.getUniqueCount() : 2;
            suggestedTest = (uniqueX <= 2) ? "WELCH_T_TEST" : "ONE_WAY_ANOVA";
            questionType = "DIFFERENCE";
        } else {
            suggestedTest = "CHI_SQUARE";
            questionType = "ASSOCIATION";
        }

        // Generate Hypotheses
        List<Hypothesis> hypotheses = new ArrayList<>();

        String nullH;
        String altH;
        String rationale;

        if (suggestedTest.equals("PEARSON")) {
            nullH = String.format("There is no statistically significant linear association between %s and %s (rho = 0).", predictor, target);
            altH = String.format("There is a statistically significant linear association between %s and %s (rho != 0).", predictor, target);
            rationale = String.format("Both %s and %s are continuous numeric metrics; Pearson product-moment correlation evaluates linear covariance.", predictor, target);
        } else if (suggestedTest.equals("WELCH_T_TEST")) {
            nullH = String.format("There is no statistically significant difference in mean values of %s across groups of %s (mu1 = mu2).",
                    typeX.equals("NUMERIC") ? predictor : target, typeX.equals("NUMERIC") ? target : predictor);
            altH = String.format("There is a statistically significant difference in mean values of %s across groups of %s (mu1 != mu2).",
                    typeX.equals("NUMERIC") ? predictor : target, typeX.equals("NUMERIC") ? target : predictor);
            rationale = "Evaluating two independent groups with unequal sample sizes and potential heteroscedastic variances via Welch's t-test.";
        } else if (suggestedTest.equals("ONE_WAY_ANOVA")) {
            nullH = String.format("The population means of %s are equal across all levels of %s.",
                    typeX.equals("NUMERIC") ? predictor : target, typeX.equals("NUMERIC") ? target : predictor);
            altH = String.format("At least one group level of %s has a significantly different mean %s.",
                    typeX.equals("NUMERIC") ? target : predictor, typeX.equals("NUMERIC") ? predictor : target);
            rationale = "Evaluating variance across multiple independent categorical classes via One-Way ANOVA.";
        } else {
            nullH = String.format("Variables '%s' and '%s' are statistically independent (no categorical association).", predictor, target);
            altH = String.format("Variables '%s' and '%s' are statistically dependent.", predictor, target);
            rationale = "Categorical contingency table analysis via Pearson Chi-Square test of independence.";
        }

        Hypothesis primaryHypothesis = Hypothesis.builder()
                .researchQuestion(researchQuestion)
                .nullHypothesis(nullH)
                .altHypothesis(altH)
                .rationale(rationale)
                .suggestedTest(suggestedTest)
                .var1(predictor)
                .var2(target)
                .confidence(0.95)
                .rankOrder(1)
                .build();

        hypotheses.add(primaryHypothesis);

        return new QuestionAnalysisResult(predictor, target, questionType, suggestedTest, hypotheses);
    }
}
