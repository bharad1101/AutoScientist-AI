package com.autoscientist.dto.ml;

import lombok.*;

/**
 * Contribution of a single feature to the simulated outcome, measured by
 * reverting that feature to its reference value and re-predicting.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatIfContributionDto {
    private String featureName;
    private String kind;
    /** Value used in the scenario. */
    private Object scenarioValue;
    /** Reference value the feature was reverted to. */
    private Object referenceValue;
    /** Prediction with the scenario applied. */
    private Double scenarioPrediction;
    /** Prediction with this single feature reverted to its reference value. */
    private Double referencePrediction;
    /** scenarioPrediction - referencePrediction. */
    private Double delta;
    /** Absolute size of the contribution. */
    private Double absoluteContribution;
    /** INCREASE | DECREASE | NEUTRAL */
    private String direction;
}
