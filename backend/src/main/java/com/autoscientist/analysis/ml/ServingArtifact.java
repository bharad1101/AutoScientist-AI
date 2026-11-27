package com.autoscientist.analysis.ml;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Self-contained, serializable snapshot required to serve predictions for a
 * trained model after a restart. It carries the fitted model plus the exact
 * feature encoding metadata used during training.
 */
public class ServingArtifact implements Serializable {

    private static final long serialVersionUID = 1L;

    private AutoMlEngine.PredictiveModel model;
    private List<String> featureNames;
    private Map<String, Object> preprocessingConfig;
    private Map<Integer, String> reverseClassMap;
    private String targetColumn;
    private String problemType;
    private String algorithm;

    public ServingArtifact() {
    }

    public ServingArtifact(AutoMlEngine.PredictiveModel model,
                           List<String> featureNames,
                           Map<String, Object> preprocessingConfig,
                           Map<Integer, String> reverseClassMap,
                           String targetColumn,
                           String problemType,
                           String algorithm) {
        this.model = model;
        this.featureNames = featureNames;
        this.preprocessingConfig = preprocessingConfig;
        this.reverseClassMap = reverseClassMap;
        this.targetColumn = targetColumn;
        this.problemType = problemType;
        this.algorithm = algorithm;
    }

    public AutoMlEngine.PredictiveModel getModel() {
        return model;
    }

    public List<String> getFeatureNames() {
        return featureNames;
    }

    public Map<String, Object> getPreprocessingConfig() {
        return preprocessingConfig;
    }

    public Map<Integer, String> getReverseClassMap() {
        return reverseClassMap;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public String getProblemType() {
        return problemType;
    }

    public String getAlgorithm() {
        return algorithm;
    }
}
