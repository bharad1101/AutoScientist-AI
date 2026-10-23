package com.autoscientist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Append-once / update-in-place provenance record for every analysis operation
 * actually executed against a dataset.
 *
 * One row per (dataset, operation, paramsHash): re-running the same analysis
 * refreshes the existing row instead of accumulating duplicates, so the table
 * stays bounded while still answering "has this analysis ever been run, when,
 * and with what parameters?".
 *
 * This table is the single source of truth used by:
 *  - workflow stage completion (WorkflowService)
 *  - report sections that must distinguish run vs not-run (ReportGeneratorService)
 *  - the evidence graph and experiment history
 *  - Ask My Dataset citations of saved results
 */
@Entity
@Table(name = "analysis_runs",
        uniqueConstraints = @UniqueConstraint(name = "uk_analysis_run_key", columnNames = {"dataset_id", "operation", "params_hash"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "params_hash", nullable = false, length = 64)
    private String paramsHash;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "run_count")
    @Builder.Default
    private Long runCount = 1L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.runCount == null) this.runCount = 1L;
        if (this.status == null) this.status = "COMPLETED";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
