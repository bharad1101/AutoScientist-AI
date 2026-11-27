package com.autoscientist.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelPredictionResponseDto {
    private Long modelId;
    private String modelName;
    private String algorithm;
    private String problemType;
    private String targetColumn;
    private Double prediction;
    private String predictedClass;
    private Double probability;
    private Boolean probabilityAvailable;
    private Double positiveClassProbability;
    private Integer predictedClassIndex;
    private Integer positiveClassIndex;
    private String positiveClassLabel;
    private Integer probabilityClassIndex;
    private String probabilityClassLabel;
    private String probabilityStatus;
    private Double decisionThreshold;
    private Map<String, Object> appliedValues;
    private List<String> warnings;
    private List<String> unrecognisedInputs;
}
