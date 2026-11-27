package com.autoscientist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Persisted trained model artifact. The model binary is stored as a BLOB.
 * The artifact is linked to a specific experiment version for traceability.
 */
@Entity
@Table(name = "model_artifacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The experiment version that produced this model. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_version_id", nullable = false)
    private ExperimentVersion experimentVersion;

    /** Human‑readable name (e.g., "Logistic Regression"). */
    @Column(name = "model_name", nullable = false)
    private String modelName;

    /** Serialized model binary (e.g., Java object, PMML, etc.). */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
