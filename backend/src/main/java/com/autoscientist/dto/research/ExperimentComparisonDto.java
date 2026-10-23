package com.autoscientist.dto.research;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentComparisonDto {
    private Long experimentId;
    private String experimentName;
    private Long datasetId;
    private String datasetName;
    private String targetColumn;
    private String problemType;
    private List<VersionRow> versions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionRow {
        private Long versionId;
        private Integer versionNumber;
        private String bestModelName;
        private Double bestMetricValue;
        private Integer datasetVersion;
        private Long randomSeed;
        private String engineVersion;
        private LocalDateTime createdAt;
        private List<ModelRow> models;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelRow {
        private Long modelRunId;
        private String modelName;
        private String algorithm;
        private boolean best;
        private Map<String, Double> trainMetrics;
        private Map<String, Double> valMetrics;
        private Map<String, Double> testMetrics;
        private Integer trainRows;
        private Integer valRows;
        private Integer testRows;
    }
}
