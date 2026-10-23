package com.autoscientist.dto.ask;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartDto {
    private String type; // histogram | bar | line
    private String title;
    private List<String> labels;
    private List<Double> values;
    private String xLabel;
    private String yLabel;
}
