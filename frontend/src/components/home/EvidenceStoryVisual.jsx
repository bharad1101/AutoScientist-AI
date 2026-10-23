import React from 'react';

/**
 * Chapter 4: Explain and Investigate
 * "Understand what your models reveal."
 * Covers feature contributions, demographic fairness audits (EEOC 80% rule),
 * distribution drift monitoring, and evidence graph connections.
 * Includes the mandatory scientific disclaimer regarding analytical tools vs causal proof.
 */
export default function EvidenceStoryVisual() {
  const contributions = [
    { feature: 'biomarker_a > 60', effect: '+0.32', dir: 'positive', desc: 'Increases likelihood of target response' },
    { feature: 'treatment_arm == Arm B', effect: '+0.24', dir: 'positive', desc: 'Positive therapeutic response' },
    { feature: 'systolic_bp > 140', effect: '-0.18', dir: 'negative', desc: 'Negative influence on primary score' },
    { feature: 'age > 65', effect: '-0.09', dir: 'negative', desc: 'Marginal negative contribution' },
  ];

  return (
    <section id="chapter-explain" className="story-chapter">
      <div className="container">
        <div className="text-center max-w-75 mx-auto mb-5">
          <div className="chapter-eyebrow justify-content-center">
            <i className="bi bi-shield-check"></i>
            <span>Chapter 04 — Explain and Investigate</span>
          </div>

          <h2 className="chapter-title">
            Understand what your models reveal.
          </h2>

          <p className="chapter-lead mx-auto">
            Dissect complex model decisions down to individual contributions. Audit demographic parity with statistical fairness checks, monitor distribution drift, and map your discoveries into an evidence graph.
          </p>
        </div>

        <div className="investigation-grid mb-4">
          {/* 1. Feature Contributions */}
          <div className="home-card">
            <div className="home-card-header">
              <div className="d-flex align-items-center gap-2">
                <i className="bi bi-layers-half text-highlight-cyan"></i>
                <span className="fw-bold text-white small">Feature Contributions</span>
              </div>
              <span className="illustrative-tag">
                <i className="bi bi-info-circle"></i> Illustrative
              </span>
            </div>
            <div className="home-card-body">
              <p className="small text-muted mb-3">
                Local feature contributions decompose model predictions into directional factors:
              </p>
              <div className="d-flex flex-column gap-2 font-monospace small">
                {contributions.map((c, i) => (
                  <div key={i} className="p-2 rounded bg-dark border border-white border-opacity-10 d-flex justify-content-between align-items-center">
                    <div>
                      <span className="text-white">{c.feature}</span>
                    </div>
                    <span className={`fw-bold ${c.dir === 'positive' ? 'text-success' : 'text-danger'}`}>
                      {c.effect}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* 2. Fairness Audit */}
          <div className="home-card">
            <div className="home-card-header">
              <div className="d-flex align-items-center gap-2">
                <i className="bi bi-person-check text-highlight-indigo"></i>
                <span className="fw-bold text-white small">Fairness &amp; Parity Audit</span>
              </div>
              <span className="badge bg-success bg-opacity-25 text-success border border-success border-opacity-25 small">
                EEOC 80% Passed
              </span>
            </div>
            <div className="home-card-body">
              <div className="d-flex justify-content-between align-items-center mb-1">
                <span className="small text-muted">Disparate Impact Ratio:</span>
                <strong className="text-highlight-cyan font-monospace">0.88</strong>
              </div>
              <div className="fairness-meter">
                <div className="fairness-bar" style={{ width: '88%' }}></div>
              </div>
              <div className="d-flex justify-content-between small text-muted font-monospace mb-3">
                <span>0.0</span>
                <span className="text-warning">Threshold: 0.80</span>
                <span>1.0 Parity</span>
              </div>
              <p className="small text-muted mb-0">
                Audits model selections against protected cohorts (e.g. demographic subgroups) according to the Four-Fifths rule to safeguard algorithmic fairness.
              </p>
            </div>
          </div>

          {/* 3. Drift Monitoring */}
          <div className="home-card">
            <div className="home-card-header">
              <div className="d-flex align-items-center gap-2">
                <i className="bi bi-activity text-highlight-cyan"></i>
                <span className="fw-bold text-white small">Distribution Drift Monitor</span>
              </div>
              <span className="badge bg-primary bg-opacity-25 text-info border border-info border-opacity-50 small">
                KS Stable
              </span>
            </div>
            <div className="home-card-body">
              <div className="p-2 rounded bg-dark border border-white border-opacity-10 mb-3 font-monospace small">
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Test:</span>
                  <span className="text-white">Kolmogorov-Smirnov</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">KS Statistic (D):</span>
                  <span className="text-highlight-cyan">0.042</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">p-value:</span>
                  <span className="text-success">0.68 (No Drift)</span>
                </div>
              </div>
              <p className="small text-muted mb-0">
                Detects covariate shifts between baseline cohorts and newly uploaded batches before model degradation impacts downstream findings.
              </p>
            </div>
          </div>

          {/* 4. Evidence Graph */}
          <div className="home-card">
            <div className="home-card-header">
              <div className="d-flex align-items-center gap-2">
                <i className="bi bi-diagram-3-fill text-highlight-violet"></i>
                <span className="fw-bold text-white small">Evidence Graph Provenance</span>
              </div>
              <span className="illustrative-tag">
                <i className="bi bi-info-circle"></i> Graph
              </span>
            </div>
            <div className="home-card-body">
              <div className="p-2 rounded bg-dark border border-white border-opacity-10 mb-2 font-monospace small">
                <div className="d-flex align-items-center gap-2 text-highlight-cyan">
                  <i className="bi bi-question-circle"></i>
                  <span>Question #1: Biomarker response?</span>
                </div>
                <div className="ps-3 border-start border-white border-opacity-25 my-1 text-muted" style={{ fontSize: '0.75rem' }}>
                  ↓ Welch t-test t(142)=4.18, p&lt;0.001
                </div>
                <div className="d-flex align-items-center gap-2 text-highlight-indigo">
                  <i className="bi bi-award"></i>
                  <span>Finding #1: Significant acceleration</span>
                </div>
              </div>
              <p className="small text-muted mb-0">
                Links research hypotheses directly to computational proofs, datasets, and models in a queryable knowledge graph.
              </p>
            </div>
          </div>
        </div>

        {/* Non-negotiable Scientific Disclaimer */}
        <div className="scientific-disclaimer-card">
          <div className="d-flex align-items-start gap-3">
            <i className="bi bi-exclamation-triangle-fill text-highlight-indigo fs-5 mt-1"></i>
            <div>
              <h6 className="fw-bold text-white mb-1">Scientific Integrity &amp; Causation Notice</h6>
              <p className="small text-muted mb-0">
                Model explanations and feature-response curves are analytical diagnostic tools, not proof of causation. Assessment of fairness and distribution drift requires appropriate baseline data.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
