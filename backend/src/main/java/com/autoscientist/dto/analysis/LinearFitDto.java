package com.autoscientist.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Least-squares line fit with goodness-of-fit quantities. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinearFitDto {
    private Double slope;
    private Double intercept;
    private Double rSquared;
    private Integer n;
    /** Standard error of the regression (residual standard deviation). */
    private Double standardError;
}
