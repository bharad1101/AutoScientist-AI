package com.autoscientist.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureEffectDto {
    private String featureName;
    private String kind; // NUMERIC | CATEGORICAL
    private String problemType; // REGRESSION | CLASSIFICATION
    private String targetColumn;
    private Double referenceValue;
    private Double referencePrediction;
    private String referenceClass;
    private List<FeatureEffectPointDto> points;
    private List<FeatureEffectCategoryDto> categories;
    private boolean supported;
    private String unsupportedReason;
    private String scientificNote;
}
