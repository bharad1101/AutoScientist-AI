package com.autoscientist.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociationDto {
    private Long id;
    private Long datasetId;
    private String var1;
    private String var2;
    private String var1Type;
    private String var2Type;
    private String testType;
    private Double statistic;
    private Double pValue;
    private Double effectSize;
    private Integer sampleSize;
    private Double degreesOfFreedom;
    private Boolean isSignificant;
    private String interpretation;
    private Double rankScore;
    /** Lower bound of the 95% confidence interval for the primary estimate. */
    private Double ciLower;
    /** Upper bound of the 95% confidence interval for the primary estimate. */
    private Double ciUpper;
    private Double ciLevel;
    private String ciMethod;
    private String ciTarget;
    /** Populated when the test does not define a single confidence interval. */
    private String ciUnavailableReason;
    private Double effectSizeCiLower;
    private Double effectSizeCiUpper;
    private LocalDateTime createdAt;
}
