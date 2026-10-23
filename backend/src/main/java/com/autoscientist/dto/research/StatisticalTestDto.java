package com.autoscientist.dto.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticalTestDto {
    private Long id;
    private Long hypothesisId;
    private Long datasetId;
    private String testType;
    private String var1;
    private String var2;
    private Double statistic;
    private Double pValue;
    private Double alpha;
    private Double degreesOfFreedom;
    private Double effectSize;
    private String effectSizeType;
    private Integer sampleSize;
    private Boolean isStatisticallySignificant;
    private String status;
    private String interpretation;
    private Double ciLower;
    private Double ciUpper;
    private Double ciLevel;
    private String ciMethod;
    private String ciTarget;
    private String ciUnavailableReason;
    private LocalDateTime createdAt;
}
