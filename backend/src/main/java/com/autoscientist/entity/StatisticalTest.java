package com.autoscientist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "statistical_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatisticalTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hypothesis_id", nullable = false)
    private Hypothesis hypothesis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "test_type", nullable = false, length = 100)
    private String testType;

    @Column(nullable = false)
    private String var1;

    @Column
    private String var2;

    @Column(nullable = false)
    private Double statistic;

    @Column(name = "p_value", nullable = false)
    private Double pValue;

    @Column
    @Builder.Default
    private Double alpha = 0.05;

    @Column(name = "degrees_of_freedom")
    private Double degreesOfFreedom;

    @Column(name = "effect_size", nullable = false)
    private Double effectSize;

    @Column(name = "effect_size_type", nullable = false, length = 50)
    private String effectSizeType;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "is_statistically_significant", nullable = false)
    @Builder.Default
    private Boolean isStatisticallySignificant = false;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "COMPLETED"; // COMPLETED, INSUFFICIENT_SAMPLE, ZERO_VARIANCE

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String interpretation;

    /** Lower bound of the 95% confidence interval for the primary estimate. */
    @Column(name = "ci_lower")
    private Double ciLower;

    /** Upper bound of the 95% confidence interval for the primary estimate. */
    @Column(name = "ci_upper")
    private Double ciUpper;

    @Column(name = "ci_level")
    private Double ciLevel;

    @Column(name = "ci_method", length = 120)
    private String ciMethod;

    @Column(name = "ci_target", length = 160)
    private String ciTarget;

    @Column(name = "ci_unavailable_reason", length = 400)
    private String ciUnavailableReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.alpha == null) this.alpha = 0.05;
        if (this.status == null) this.status = "COMPLETED";
    }
}
