package com.autoscientist.dto.dashboard;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily research-activity histogram backing the dashboard activity chart.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySeriesDto {
    /** First day covered by the histogram (inclusive). */
    private LocalDate startDate;
    /** Last day covered by the histogram (inclusive). */
    private LocalDate endDate;
    /** Number of recorded events inside the covered window. */
    private int totalEvents;
    /** Number of recorded events across the owner's whole history. */
    private int lifetimeEvents;
    /** True when the window was truncated to the most recent days. */
    private boolean truncated;
    private List<ActivityBucketDto> buckets;
}
