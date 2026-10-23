package com.autoscientist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "associations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Association {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(nullable = false)
    private String var1;

    @Column(nullable = false)
    private String var2;

    @Column(name = "var1_type", nullable = false, length = 50)
    private String var1Type;

    @Column(name = "var2_type", nullable = false, length = 50)
    private String var2Type;

    @Column(name = "test_type", nullable = false, length = 50)
    private String testType; // PEARSON, SPEARMAN, WELCH_T_TEST, ONE_WAY_ANOVA, CHI_SQUARE

    @Column(nullable = false)
    private Double statistic;

    @Column(name = "p_value", nullable = false)
    private Double pValue;

    @Column(name = "effect_size", nullable = false)
    private Double effectSize;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "degrees_of_freedom")
    private Double degreesOfFreedom;

    @Column(name = "is_significant", nullable = false)
    @Builder.Default
    private Boolean isSignificant = false;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String interpretation;

    @Column(name = "rank_score", nullable = false)
    @Builder.Default
    private Double rankScore = 0.0;

    /** Lower bound of the 95% confidence interval for the test's primary estimate. */
    @Column(name = "ci_lower")
    private Double ciLower;

    /** Upper bound of the 95% confidence interval for the test's primary estimate. */
    @Column(name = "ci_upper")
    private Double ciUpper;

    /** Confidence level of the interval (0.95). */
    @Column(name = "ci_level")
    private Double ciLevel;

    /** How the interval was computed (e.g. "Fisher z-transform (95% CI)"). */
    @Column(name = "ci_method", length = 120)
    private String ciMethod;

    /** What the interval describes (e.g. "Pearson r", "Mean difference (a - b)"). */
    @Column(name = "ci_target", length = 160)
    private String ciTarget;

    /** Why no interval is available, when the test does not define one. */
    @Column(name = "ci_unavailable_reason", length = 400)
    private String ciUnavailableReason;

    /** Lower bound of the approximate 95% CI for the effect size. */
    @Column(name = "effect_size_ci_lower")
    private Double effectSizeCiLower;

    /** Upper bound of the approximate 95% CI for the effect size. */
    @Column(name = "effect_size_ci_upper")
    private Double effectSizeCiUpper;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
