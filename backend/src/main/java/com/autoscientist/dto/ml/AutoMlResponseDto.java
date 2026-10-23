package com.autoscientist.dto.ml;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoMlResponseDto {
    private Long experimentId;
    private Long experimentVersionId;
    private String experimentName;
    private String problemType;
    private String targetColumn;
    private List<ModelRunDto> models;
    private ModelRunDto bestModel;
    private List<FeatureImportanceDto> featureImportances;
    private FairnessDto fairness;
    private String scientificSummary;
    /** AVAILABLE | AVAILABLE_IN_MEMORY | UNAVAILABLE | UNAVAILABLE_RETRAIN */
    private String explanationStatus;
    private Integer datasetVersion;
    private Long randomSeed;
    private String engineVersion;
}
