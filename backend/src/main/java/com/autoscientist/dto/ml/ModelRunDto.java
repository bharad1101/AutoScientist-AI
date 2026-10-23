package com.autoscientist.dto.ml;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelRunDto {
    private Long id;
    private String modelName;
    private String algorithm;
    private Map<String, Object> hyperparameters;
    private Map<String, Double> trainMetrics;
    private Map<String, Double> valMetrics;
    private Map<String, Double> testMetrics;
    private Boolean isBestModel;
    private Map<String, Object> fairnessMetrics;
    private java.util.List<FeatureImportanceDto> featureImportances;
    private Map<String, Object> evaluation;
    private Long randomSeed;
    private Integer trainRows;
    private Integer valRows;
    private Integer testRows;
}
