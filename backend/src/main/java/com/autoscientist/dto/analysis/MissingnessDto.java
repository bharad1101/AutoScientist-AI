package com.autoscientist.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingnessDto {
    private String variable;
    private long missingCount;
    private long totalCount;
    private double missingPct;
}
