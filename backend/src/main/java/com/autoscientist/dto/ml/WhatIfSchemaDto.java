package com.autoscientist.dto.ml;

import lombok.*;

import java.util.List;

/**
 * Description of the raw inputs a trained model expects, together with the
 * prediction of the reference profile (median/mode of every feature).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatIfSchemaDto {
    private String targetColumn;
    private String problemType;
    private List<WhatIfFeatureDto> features;
    private Double referencePrediction;
    private String referenceClass;
    private String referenceDescription;
}
