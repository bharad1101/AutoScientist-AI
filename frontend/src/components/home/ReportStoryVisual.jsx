import React from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * Chapter 5: Findings and Reports
 * "Make every result easier to examine."
 * Features a scientific publication-ready report preview, evidence graph synthesis,
 * multi-format export cards (PDF, Markdown, XLSX, CSV), and the concluding CTA:
 * "Your next discovery starts with a dataset."
 */
export default function ReportStoryVisual({ isAuthenticated }) {
  const navigate = useNavigate();

  const exportFormats = [
    { format: 'PDF', icon: 'bi-file-earmark-pdf', desc: 'Publication-ready layout with embedded charts & tables' },
    { format: 'Markdown', icon: 'bi-markdown', desc: 'Reproducible research documentation for notebooks & Git' },
    { format: 'Excel (XLSX)', icon: 'bi-file-earmark-excel', desc: 'Multi-tab spreadsheets with raw data & statistical metrics' },
    { format: 'CSV', icon: 'bi-file-earmark-text', desc: 'Standard flat analytical tables for downstream tooling' },
  ];

  return (
    <section id="chapter-reports" className="story-chapter pb-0">
      <div className="container">
        <div className="row align-items-center g-5 mb-5">
          <div className="col-lg-5">
            <div className="chapter-eyebrow">
              <i className="bi bi-file-earmark-text"></i>
              <span>Chapter 05 — Findings and Reports</span>
            </div>

            <h2 className="chapter-title">
              Make every result easier to examine.
            </h2>

            <p className="chapter-lead">
              Synthesize findings into an evidence-backed scientific report with full computational provenance. Export publication-grade documents with one click in PDF, Markdown, XLSX, or CSV formats.
            </p>

            <div className="d-flex flex-column gap-3 mb-4">
              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-journal-medical fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Full Provenance &amp; Audit Trail</h6>
                  <p className="small mb-0 text-muted">
                    Every figure, table, and conclusion records its execution seed, dataset version, algorithm parameters, and mathematical degrees of freedom.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-indigo">
                  <i className="bi bi-file-earmark-arrow-down fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Multi-Format Scientific Export</h6>
                  <p className="small mb-0 text-muted">
                    Generate formatted PDF reports with high-resolution plots, portable Markdown for reproducible lab notebooks, or multi-sheet Excel workbooks.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-check-all fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Zero Guesswork Reporting</h6>
                  <p className="small mb-0 text-muted">
                    Unrun analyses are transparently reported as unrun — never inferred or fabricated.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="col-lg-7">
            <div className="report-preview-sheet">
              <div className="d-flex justify-content-between align-items-center pb-3 mb-3 border-bottom border-white border-opacity-10">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-cpu-fill text-highlight-cyan fs-5"></i>
                  <div>
                    <h5 className="fw-bold text-white mb-0">AutoScientist AI — Research Findings Summary</h5>
                    <div className="small text-muted font-monospace" style={{ fontSize: '0.72rem' }}>
                      Report ID: AS-2026-0842 · Dataset: cohort_biomarkers.xlsx
                    </div>
                  </div>
                </div>
                <span className="illustrative-tag">
                  <i className="bi bi-info-circle"></i> Illustrative Report
                </span>
              </div>

              <div className="mb-3">
                <h6 className="fw-bold text-white mb-1">1. Executive Summary</h6>
                <p className="small text-muted mb-2">
                  Analysis of cohort clinical trial data (N = 5,412) demonstrates statistically significant association between biomarker levels and treatment outcomes (p &lt; 0.001, Cohen's d = 0.74).
                </p>
              </div>

              <div className="mb-3">
                <h6 className="fw-bold text-white mb-1">2. Hypothesis Testing &amp; Statistical Rigor</h6>
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 font-monospace small">
                  <div className="text-highlight-cyan">→ Welch Two-Sample t(142) = 4.18, p = 5.2e-5 (Significant)</div>
                  <div className="text-muted">→ ANOVA across treatment arms: F(2, 5409) = 42.8, η² = 0.28</div>
                </div>
              </div>

              <div className="mb-3">
                <h6 className="fw-bold text-white mb-1">3. Predictive Modeling &amp; Explanations</h6>
                <p className="small text-muted mb-0">
                  Random Forest Classifier achieved cross-validated AUC of 0.892 (Seed 42). Permutation importance isolates <code>biomarker_a</code> (34.2%) and <code>age</code> (21.5%) as leading predictive features.
                </p>
              </div>

              <div className="pt-3 border-top border-white border-opacity-10">
                <div className="small text-muted mb-2 fw-semibold text-uppercase tracking-wider">
                  Supported Export Formats:
                </div>
                <div className="row g-2">
                  {exportFormats.map((f) => (
                    <div key={f.format} className="col-sm-6">
                      <div className="export-badge">
                        <i className={`bi ${f.icon} text-highlight-cyan fs-5`}></i>
                        <div>
                          <div className="text-white small fw-bold">{f.format}</div>
                          <div className="text-muted" style={{ fontSize: '0.7rem' }}>{f.desc}</div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Final CTA Banner */}
        <div className="final-cta-section">
          <div className="chapter-eyebrow justify-content-center">
            <i className="bi bi-stars"></i>
            <span>Start Your Investigation</span>
          </div>

          <h2 className="display-5 fw-bold text-white mb-3">
            Your next discovery starts with a dataset.
          </h2>

          <p className="lead text-muted mx-auto mb-4" style={{ maxWidth: '600px' }}>
            Turn raw scientific data into reproducible, evidence-backed insights with automated exploratory analysis, hypothesis testing, and explainable AI.
          </p>

          <div className="d-flex justify-content-center gap-3 flex-wrap">
            <button
              className="btn-discovery-primary btn-lg"
              onClick={() => navigate(isAuthenticated ? '/datasets' : '/register')}
            >
              <i className="bi bi-cloud-arrow-up"></i>
              <span>Start a Discovery</span>
            </button>

            {!isAuthenticated && (
              <button
                className="btn-discovery-secondary btn-lg"
                onClick={() => navigate('/login')}
              >
                <i className="bi bi-box-arrow-in-right"></i>
                <span>Sign In to Account</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Public Footer */}
      <footer className="home-footer">
        <div className="container">
          <div className="row align-items-center g-3">
            <div className="col-md-6 text-center text-md-start">
              <div className="d-flex align-items-center justify-content-center justify-content-md-start gap-2 mb-1">
                <i className="bi bi-cpu-fill text-highlight-cyan fs-5"></i>
                <span className="fw-bold text-white">AutoScientist AI</span>
              </div>
              <p className="small text-muted mb-0">
                From Raw Data to Evidence-Based Scientific Discovery.
              </p>
            </div>
            <div className="col-md-6 text-center text-md-end small text-muted">
              <div>Statistical Rigor · Reproducible ML · Non-Destructive Ingestion</div>
              <div className="text-dim mt-1">© {new Date().getFullYear()} AutoScientist AI. All rights reserved.</div>
            </div>
          </div>
        </div>
      </footer>
    </section>
  );
}
