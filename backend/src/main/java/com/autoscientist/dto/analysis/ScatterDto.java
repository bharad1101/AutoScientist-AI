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
public class ScatterDto {
    private String xVariable;
    private String yVariable;
    private List<double[]> points;
    private long totalRows;
    private boolean downsampled;
    /** Ordinary-least-squares fit of y on x (null when the sample is too small). */
    private LinearFitDto fit;
}
