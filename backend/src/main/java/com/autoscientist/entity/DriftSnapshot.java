package com.autoscientist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted result of a drift comparison. Keeping the payload makes drift
 * evidence reproducible in reports and the evidence graph instead of being a
 * one-off, un-recoverable computation.
 */
@Entity
@Table(name = "drift_snapshots", indexes = {
        @Index(name = "idx_drift_snapshot_dataset", columnList = "dataset_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriftSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "reference_dataset_id")
    private Long referenceDatasetId;

    @Column(name = "reference_dataset_name")
    private String referenceDatasetName;

    @Column(name = "overall_status", length = 32)
    private String overallStatus;

    @Column(name = "overall_drift_index")
    private Double overallDriftIndex;

    @Column(name = "report_json", columnDefinition = "LONGTEXT", nullable = false)
    private String reportJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
