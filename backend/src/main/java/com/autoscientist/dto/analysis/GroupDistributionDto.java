package com.autoscientist.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Distribution of one numeric variable inside one cohort of a categorical split.
 * All cohorts of a split share the same bin edges so density curves are comparable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDistributionDto {
    private String groupName;
    private Integer count;
    private Double mean;
    private Double stdDev;
    private Double median;
    private List<Double> binStarts;
    private List<Double> binEnds;
    private List<Long> counts;
    /** Share of the group falling in each bin (0..1) — used for density overlays. */
    private List<Double> proportions;
}
