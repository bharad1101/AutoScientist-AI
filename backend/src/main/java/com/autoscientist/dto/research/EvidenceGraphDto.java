package com.autoscientist.dto.research;

import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceGraphDto {

    private Long datasetId;
    private String datasetName;
    private List<Node> nodes;
    private List<Edge> edges;
    private ReliabilityScorecard reliability;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private String id;
        private String type;
        private String label;
        private String detail;
        @Builder.Default
        private Map<String, Object> meta = new LinkedHashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        private String from;
        private String to;
        private String type;
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReliabilityScorecard {
        private int score;
        private String grade;
        private List<Check> checks;
        private List<String> limitations;

        public static ReliabilityScorecard empty() {
            return ReliabilityScorecard.builder()
                    .score(0).grade("NOT_ASSESSED")
                    .checks(new ArrayList<>()).limitations(new ArrayList<>())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Check {
        private String id;
        private String label;
        private String status; // PASS, WARN, FAIL, NA
        private String detail;
        private int weight;
    }
}
