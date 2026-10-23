package com.autoscientist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "experiment_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "preprocessing_json", columnDefinition = "LONGTEXT")
    private String preprocessingJson;

    @Column(name = "feature_selection_json", columnDefinition = "LONGTEXT")
    private String featureSelectionJson;

    @Column(name = "models_summary_json", columnDefinition = "LONGTEXT")
    private String modelsSummaryJson;

    @Column(name = "best_model_name", length = 100)
    private String bestModelName;

    @Column(name = "best_metric_value")
    private Double bestMetricValue;

    /** Version of the dataset this version was computed against. */
    @Column(name = "dataset_version")
    private Integer datasetVersion;

    /** Random seed used by the run for reproducibility. */
    @Column(name = "random_seed")
    private Long randomSeed;

    /** Engine/app version string recorded at execution time. */
    @Column(name = "engine_version", length = 64)
    private String engineVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
