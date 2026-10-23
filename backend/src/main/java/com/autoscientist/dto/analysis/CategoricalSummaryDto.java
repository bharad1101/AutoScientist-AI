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
public class CategoricalSummaryDto {
    private String variable;
    private long uniqueCount;
    private long missingCount;
    private List<CategoryCountDto> topCategories;
}
