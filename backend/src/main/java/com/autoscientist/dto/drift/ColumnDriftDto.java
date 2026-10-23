package com.autoscientist.dto.drift;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnDriftDto {
    private String columnName;
    private String dataType;
    private double psiScore;
    private String driftStatus; // NO_DRIFT, MODERATE_DRIFT, SIGNIFICANT_DRIFT
    private double pValue;
    private Double ksStatistic;
    private Double ksPValue;
    private Double referenceMean;
    private Double targetMean;
    private Double meanShiftPct;
    private Map<String, Double> referenceDistribution;
    private Map<String, Double> targetDistribution;
    /** Numeric columns only: shared bin edges used for the PSI computation. */
    private List<Double> binEdges;
    /** Numeric columns only: share of the reference dataset per bin (0..1). */
    private List<Double> referenceBinProportions;
    /** Numeric columns only: share of the target dataset per bin (0..1). */
    private List<Double> targetBinProportions;
    private String interpretation;
}
