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
public class HistogramDto {
    private String variable;
    private List<Double> binStarts;
    private List<Double> binEnds;
    private List<Long> counts;
    private long nonMissing;
}
