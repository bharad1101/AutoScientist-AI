package com.autoscientist.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureEffectCategoryDto {
    private String category;
    private double prediction;
    private Double probability;
}
