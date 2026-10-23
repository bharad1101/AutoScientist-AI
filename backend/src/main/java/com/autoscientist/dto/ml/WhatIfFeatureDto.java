package com.autoscientist.dto.ml;

import lombok.*;

import java.util.List;

/**
 * One raw input of the trained model, described the way the user sees it
 * (i.e. before imputation / one-hot / standardisation).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatIfFeatureDto {
    private String name;
    /** NUMERIC or CATEGORICAL. */
    private String kind;
    /** Reference value used when the user leaves the field untouched (median / mode). */
    private Object referenceValue;
    private Double min;
    private Double max;
    private List<String> categories;
}
