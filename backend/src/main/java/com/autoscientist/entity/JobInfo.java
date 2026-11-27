package com.autoscientist.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
public class JobInfo {

    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELED
    }

    public enum JobType {
        INGESTION,
        ASSOCIATION,
        EDA,
        AUTOML,
        OTHER
    }



    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private JobType type; // e.g., INGESTION, ASSOCIATION, EDA, AUTOML, OTHER

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    /** Progress 0‑100 */
    private int progress = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    /** Path to a transient file (e.g., uploaded dataset) used by the job. */
    private String transientFilePath;

    /** Owner of the job. Null only for legacy rows created before ownership was tracked. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** Dataset this job operates on (EDA, ASSOCIATION, AUTOML). */
    private Long datasetId;

    /** AutoML target column, when applicable. */
    private String targetColumn;

    /** AutoML problem type, when applicable. */
    private String problemType;

    // ---------- getters & setters ----------
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public JobType getType() { return type; }
    public void setType(JobType type) { this.type = type; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getTransientFilePath() { return transientFilePath; }
    public void setTransientFilePath(String transientFilePath) { this.transientFilePath = transientFilePath; }
    @JsonIgnore
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public String getProblemType() { return problemType; }
    public void setProblemType(String problemType) { this.problemType = problemType; }
}
