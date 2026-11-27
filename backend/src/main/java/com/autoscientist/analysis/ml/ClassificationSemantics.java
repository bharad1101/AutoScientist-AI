package com.autoscientist.analysis.ml;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClassificationSemantics {

    public static final double BINARY_DECISION_THRESHOLD = 0.5;
    public static final int BINARY_POSITIVE_CLASS_INDEX = 1;
    public static final String PROBABILITY_STATUS_AVAILABLE = "AVAILABLE";
    public static final String PROBABILITY_STATUS_NOT_BINARY = "NOT_BINARY";
    public static final String PROBABILITY_STATUS_UNAVAILABLE_NO_SCORES = "UNAVAILABLE_NO_SCORES";

    private ClassificationSemantics() {
    }

    public static boolean isBinaryClassification(String problemType, Map<Integer, String> reverseClassMap) {
        return "CLASSIFICATION".equals(problemType)
                && reverseClassMap != null
                && reverseClassMap.size() == 2
                && reverseClassMap.containsKey(BINARY_POSITIVE_CLASS_INDEX);
    }

    public static Integer positiveClassIndex(String problemType, Map<Integer, String> reverseClassMap) {
        return isBinaryClassification(problemType, reverseClassMap) ? BINARY_POSITIVE_CLASS_INDEX : null;
    }

    public static String positiveClassLabel(String problemType, Map<Integer, String> reverseClassMap) {
        Integer index = positiveClassIndex(problemType, reverseClassMap);
        return index == null || reverseClassMap == null ? null : reverseClassMap.get(index);
    }

    public static boolean isUsableScore(double score) {
        return Double.isFinite(score) && score >= 0.0 && score <= 1.0;
    }

    public static Map<String, Object> metadata(String problemType, Map<Integer, String> reverseClassMap) {
        Integer positiveIndex = positiveClassIndex(problemType, reverseClassMap);
        String positiveLabel = positiveClassLabel(problemType, reverseClassMap);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("binaryClassification", positiveIndex != null);
        metadata.put("positiveClassIndex", positiveIndex);
        metadata.put("positiveClassLabel", positiveLabel);
        metadata.put("probabilityClassIndex", positiveIndex);
        metadata.put("probabilityClassLabel", positiveLabel);
        metadata.put("probabilityStatus", positiveIndex == null ? PROBABILITY_STATUS_NOT_BINARY : PROBABILITY_STATUS_AVAILABLE);
        metadata.put("decisionThreshold", BINARY_DECISION_THRESHOLD);
        return metadata;
    }
}
