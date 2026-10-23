package com.autoscientist.dto.dataset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetColumnDto {
    private Long id;
    private Integer columnIndex;
    private String columnName;
    private String dataType;
    private Double typeConfidence;
    private Integer missingCount;
    private Double missingPct;
    private Integer uniqueCount;
    private Double minVal;
    private Double maxVal;
    private Double meanVal;
    private Double medianVal;
    private Double stdDev;
    private Double variance;
    private Double q1;
    private Double q3;
    private Double skewness;
    private String categoriesJson;
    private String distributionJson;
    private boolean excluded;
}
