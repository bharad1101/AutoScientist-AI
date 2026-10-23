package com.autoscientist.dto.ml;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FairnessDto {
    private String sensitiveColumn;
    private double demographicParityRatio;
    private String disparateImpactStatus; // PASS, FLAGGED, NOT_APPLICABLE
    private Map<String, Double> groupPositiveRates;
    private Map<String, Double> groupErrorRates;
    /** Number of validation rows behind each group statistic. */
    private Map<String, Integer> groupSampleSizes;
    private String recommendation;
}
