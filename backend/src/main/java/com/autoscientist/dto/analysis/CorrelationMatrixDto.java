package com.autoscientist.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationMatrixDto {
    private List<String> variables;
    private double[][] matrix;
    /**
     * Two-sided p-value of each pairwise Pearson test (null hypothesis: rho = 0).
     * Null when the engine could not produce significance levels.
     */
    private double[][] pValues;
    /** Complete-case sample size used for each variable pair. */
    private int[] sampleSizes;
}
