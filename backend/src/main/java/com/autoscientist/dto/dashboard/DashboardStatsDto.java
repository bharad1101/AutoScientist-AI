package com.autoscientist.dto.dashboard;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {
    private long totalDatasets;
    private long totalObservations;
    private long totalAssociations;
    private long totalResearchQuestions;
    private long totalExperiments;
    private long totalModelsTrained;
    private long totalFindings;
    private long totalReports;
    private double averageQualityScore;
    private List<TimelineEventDto> recentActivity;
    /** Daily research-activity histogram (real persisted events only). */
    private ActivitySeriesDto activity;
}
