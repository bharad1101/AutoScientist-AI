package com.autoscientist.dto.dashboard;

import lombok.*;

import java.time.LocalDate;

/**
 * One calendar day of recorded research activity.
 *
 * Every counter is derived from persisted rows (datasets, findings, research
 * questions, experiments, reports) — no synthetic activity is ever reported.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityBucketDto {
    private LocalDate date;
    /** Total number of recorded events on this day. */
    private int total;
    private int datasetUploads;
    private int findings;
    private int questions;
    private int experiments;
    private int reports;
}
