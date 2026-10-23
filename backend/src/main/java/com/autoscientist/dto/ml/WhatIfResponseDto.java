package com.autoscientist.dto.ml;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatIfResponseDto {
    private String targetColumn;
    private String problemType;
    private Double predictedValue;
    private String predictedClass;
    private Double confidence;
    /** false when the model cannot produce an honest probability score. */
    private boolean confidenceAvailable;
    /** Mean of the training target — context for the predicted value. */
    private Double baselineMean;
    private Double deltaFromBaseline;
    private List<String> keyInfluences;

    /** Prediction of the reference profile (median/mode of every feature). */
    private Double referencePrediction;
    private String referenceDescription;
    /** Raw features the model expects, so the UI can render correct inputs. */
    private List<WhatIfFeatureDto> featureSchema;
    /** Per-feature contribution to the shift away from the reference profile. */
    private List<WhatIfContributionDto> contributions;
    /** Method used to attribute the shift (e.g. SINGLE-FEATURE ABLATION). */
    private String contributionMethod;
    /** true when the attribution is exact for the model family in use. */
    private boolean contributionsExact;
    /** Values actually applied after imputation / encoding corrections. */
    private Map<String, Object> appliedValues;
    /** Notes about adjusted or extrapolated inputs. */
    private List<String> warnings;
    /** Inputs the model does not recognise. */
    private List<String> unrecognisedInputs;
}
