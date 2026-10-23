import React, { useState } from 'react';

/**
 * Chapter 2: Patterns Emerge
 * "Find the patterns hiding in plain sight."
 * Visualizes correlation matrices with exact p-values, ranked associations
 * with effect sizes (Cohen's d, Cramer's V), and grouped box plots.
 */
export default function AnalyticsStoryVisual() {
  const [activeTab, setActiveTab] = useState('correlations');
  const [selectedCell, setSelectedCell] = useState({ r: '+0.74', p: '2.1e-16', pair: 'cholesterol × systolic_bp' });

  const correlationMatrix = [
    { var1: 'age', var2: 'age', r: '1.00', p: '0.00' },
    { var1: 'age', var2: 'cholesterol', r: '+0.52', p: '3.4e-8' },
    { var1: 'age', var2: 'systolic_bp', r: '+0.48', p: '1.1e-6' },
    { var1: 'age', var2: 'response_score', r: '+0.31', p: '4.8e-4' },

    { var1: 'cholesterol', var2: 'age', r: '+0.52', p: '3.4e-8' },
    { var1: 'cholesterol', var2: 'cholesterol', r: '1.00', p: '0.00' },
    { var1: 'cholesterol', var2: 'systolic_bp', r: '+0.74', p: '2.1e-16' },
    { var1: 'cholesterol', var2: 'response_score', r: '+0.58', p: '8.2e-10' },

    { var1: 'systolic_bp', var2: 'age', r: '+0.48', p: '1.1e-6' },
    { var1: 'systolic_bp', var2: 'cholesterol', r: '+0.74', p: '2.1e-16' },
    { var1: 'systolic_bp', var2: 'systolic_bp', r: '1.00', p: '0.00' },
    { var1: 'systolic_bp', var2: 'response_score', r: '+0.63', p: '1.5e-12' },

    { var1: 'response_score', var2: 'age', r: '+0.31', p: '4.8e-4' },
    { var1: 'response_score', var2: 'cholesterol', r: '+0.58', p: '8.2e-10' },
    { var1: 'response_score', var2: 'systolic_bp', r: '+0.63', p: '1.5e-12' },
    { var1: 'response_score', var2: 'response_score', r: '1.00', p: '0.00' },
  ];

  const rankedAssociations = [
    { rank: 1, pair: 'cholesterol × systolic_bp', type: 'Pearson', stat: 'r = 0.74', pval: 'p = 2.1e-16', effect: 'Large', ci: '[0.68, 0.79]' },
    { rank: 2, pair: 'treatment_arm × response_score', type: 'ANOVA', stat: 'F = 42.8', pval: 'p = 4.2e-14', effect: 'η² = 0.28', ci: '[0.22, 0.35]' },
    { rank: 3, pair: 'systolic_bp × response_score', type: 'Spearman', stat: 'ρ = 0.63', pval: 'p = 1.5e-12', effect: 'Moderate-Large', ci: '[0.57, 0.69]' },
    { rank: 4, pair: 'treatment_arm × adverse_events', type: 'Chi-Square', stat: 'χ² = 18.4', pval: 'p = 1.1e-4', effect: 'Cramér V = 0.19', ci: '[0.11, 0.27]' },
  ];

  const boxPlotGroups = [
    { name: 'Control (Placebo)', min: 38, q1: 46, median: 52, q3: 59, max: 68, n: 1804 },
    { name: 'Arm A (Active)', min: 54, q1: 67, median: 75, q3: 82, max: 94, n: 1812 },
    { name: 'Arm B (High Dose)', min: 65, q1: 81, median: 89, q3: 95, max: 100, n: 1796 },
  ];

  return (
    <section id="chapter-patterns" className="story-chapter">
      <div className="container">
        <div className="row align-items-center g-5">
          <div className="col-lg-5 order-lg-2">
            <div className="chapter-eyebrow">
              <i className="bi bi-graph-up-arrow"></i>
              <span>Chapter 02 — Patterns Emerge</span>
            </div>

            <h2 className="chapter-title">
              Find the patterns hiding in plain sight.
            </h2>

            <p className="chapter-lead">
              Automated exploratory data analysis uncovers hidden associations without manual guesswork. Every discovered pattern is paired with exact statistical significance and effect sizes.
            </p>

            <div className="d-flex flex-column gap-3 mb-4">
              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-grid-3x3 fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Full Correlation Matrix & p-Values</h6>
                  <p className="small mb-0 text-muted">
                    Pearson and Spearman matrices with exact two-tailed hypothesis tests so you can distinguish genuine trends from random variance.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-indigo">
                  <i className="bi bi-sort-numeric-down fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Ranked Association Leaderboard</h6>
                  <p className="small mb-0 text-muted">
                    Bivariate relationships ranked by standardized effect sizes (Cohen's d, η², Cramér's V) with 95% confidence intervals.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-bar-chart-steps fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Five-Number Box Plot Summaries</h6>
                  <p className="small mb-0 text-muted">
                    Automated group distributions comparing medians, interquartile ranges, and outlier thresholds across cohort categories.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="col-lg-7 order-lg-1">
            <div className="home-card">
              <div className="home-card-header">
                <div className="tab-nav-pills w-100 mb-0">
                  <button
                    className={`tab-nav-btn ${activeTab === 'correlations' ? 'active' : ''}`}
                    onClick={() => setActiveTab('correlations')}
                  >
                    <i className="bi bi-grid-3x3 me-1"></i> Correlation Heatmap
                  </button>
                  <button
                    className={`tab-nav-btn ${activeTab === 'associations' ? 'active' : ''}`}
                    onClick={() => setActiveTab('associations')}
                  >
                    <i className="bi bi-sort-down me-1"></i> Ranked Associations
                  </button>
                  <button
                    className={`tab-nav-btn ${activeTab === 'boxplots' ? 'active' : ''}`}
                    onClick={() => setActiveTab('boxplots')}
                  >
                    <i className="bi bi-bar-chart me-1"></i> Group Box Plots
                  </button>
                </div>
              </div>

              <div className="home-card-body">
                {/* View 1: Correlation Heatmap */}
                {activeTab === 'correlations' && (
                  <div>
                    <div className="d-flex justify-content-between align-items-center mb-2">
                      <span className="small text-muted">Click any cell to inspect correlation coefficient & p-value:</span>
                      <span className="illustrative-tag">
                        <i className="bi bi-info-circle"></i> Illustrative Data
                      </span>
                    </div>

                    <div className="heatmap-grid mb-3">
                      {correlationMatrix.map((c, i) => {
                        const val = parseFloat(c.r);
                        const isDiag = c.var1 === c.var2;
                        let cellBg = '#151D2F';
                        let textColor = '#E5E7EB';

                        if (isDiag) {
                          cellBg = '#1E293B';
                          textColor = '#94A3B8';
                        } else if (val >= 0.7) {
                          cellBg = 'rgba(6, 182, 212, 0.35)';
                          textColor = '#22D3EE';
                        } else if (val >= 0.4) {
                          cellBg = 'rgba(59, 130, 246, 0.25)';
                          textColor = '#93C5FD';
                        } else {
                          cellBg = 'rgba(99, 102, 241, 0.15)';
                          textColor = '#C7D2FE';
                        }

                        return (
                          <div
                            key={i}
                            className="heatmap-cell"
                            style={{ background: cellBg }}
                            onClick={() => setSelectedCell({ r: c.r, p: c.p, pair: `${c.var1} × ${c.var2}` })}
                          >
                            <div className="heatmap-corr" style={{ color: textColor }}>{c.r}</div>
                            <div className="heatmap-pval">{c.p === '0.00' ? 'diag' : `p=${c.p}`}</div>
                          </div>
                        );
                      })}
                    </div>

                    <div className="p-3 rounded bg-dark border border-white border-opacity-10 d-flex justify-content-between align-items-center">
                      <div>
                        <span className="small text-muted text-uppercase tracking-wider">Selected Pair: </span>
                        <strong className="text-white font-monospace ms-1">{selectedCell.pair}</strong>
                      </div>
                      <div className="d-flex gap-3 small">
                        <span>Pearson r: <strong className="text-highlight-cyan">{selectedCell.r}</strong></span>
                        <span>p-value: <strong className="text-highlight-indigo">{selectedCell.p}</strong></span>
                      </div>
                    </div>
                  </div>
                )}

                {/* View 2: Ranked Associations */}
                {activeTab === 'associations' && (
                  <div>
                    <div className="d-flex justify-content-between align-items-center mb-3">
                      <span className="small text-muted">Statistically significant bivariate tests ranked by effect size:</span>
                      <span className="illustrative-tag">
                        <i className="bi bi-info-circle"></i> Illustrative Data
                      </span>
                    </div>

                    <div className="table-responsive">
                      <table className="preview-table">
                        <thead>
                          <tr>
                            <th>#</th>
                            <th>Variable Pair</th>
                            <th>Method</th>
                            <th>Test Statistic</th>
                            <th>p-Value</th>
                            <th>Effect Size & 95% CI</th>
                          </tr>
                        </thead>
                        <tbody>
                          {rankedAssociations.map((a) => (
                            <tr key={a.rank}>
                              <td className="fw-bold text-highlight-cyan">{a.rank}</td>
                              <td className="font-monospace text-white">{a.pair}</td>
                              <td><span className="badge bg-dark border border-white border-opacity-10">{a.type}</span></td>
                              <td><code>{a.stat}</code></td>
                              <td className="text-success">{a.pval}</td>
                              <td>
                                <strong className="text-highlight-indigo">{a.effect}</strong>
                                <span className="small text-muted ms-1">{a.ci}</span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* View 3: Group Box Plots */}
                {activeTab === 'boxplots' && (
                  <div>
                    <div className="d-flex justify-content-between align-items-center mb-3">
                      <span className="small text-muted">Response score distribution across treatment arms:</span>
                      <span className="illustrative-tag">
                        <i className="bi bi-info-circle"></i> Illustrative Data
                      </span>
                    </div>

                    <div className="d-flex flex-column gap-3">
                      {boxPlotGroups.map((g) => (
                        <div key={g.name} className="p-3 rounded bg-dark border border-white border-opacity-10">
                          <div className="d-flex justify-content-between align-items-center mb-2">
                            <span className="fw-bold text-white">{g.name}</span>
                            <span className="small text-muted">n = {g.n}</span>
                          </div>
                          <div className="d-flex align-items-center gap-3">
                            <span className="small font-monospace text-muted" style={{ width: '40px' }}>0</span>
                            <div className="flex-grow-1 position-relative" style={{ height: '24px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px' }}>
                              {/* Box */}
                              <div
                                style={{
                                  position: 'absolute',
                                  left: `${g.q1}%`,
                                  width: `${g.q3 - g.q1}%`,
                                  height: '100%',
                                  background: 'rgba(34, 211, 238, 0.25)',
                                  border: '1px solid var(--home-accent-cyan)',
                                  borderRadius: '2px',
                                }}
                              />
                              {/* Median Line */}
                              <div
                                style={{
                                  position: 'absolute',
                                  left: `${g.median}%`,
                                  width: '3px',
                                  height: '100%',
                                  background: '#FFFFFF',
                                  boxShadow: '0 0 6px rgba(255,255,255,0.8)',
                                }}
                                title={`Median: ${g.median}`}
                              />
                              {/* Whiskers */}
                              <div
                                style={{
                                  position: 'absolute',
                                  left: `${g.min}%`,
                                  width: `${g.max - g.min}%`,
                                  top: '50%',
                                  height: '1px',
                                  background: 'rgba(255,255,255,0.4)',
                                  zIndex: 0,
                                }}
                              />
                            </div>
                            <span className="small font-monospace text-muted" style={{ width: '40px' }}>100</span>
                          </div>
                          <div className="d-flex justify-content-between small text-muted mt-1 font-monospace">
                            <span>Min: {g.min}</span>
                            <span>Q1: {g.q1}</span>
                            <span className="text-highlight-cyan fw-bold">Median: {g.median}</span>
                            <span>Q3: {g.q3}</span>
                            <span>Max: {g.max}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="mt-3 pt-2 border-top border-white border-opacity-10 text-end small fst-italic text-dim">
                  Illustrative benchmark dataset for scientific demonstration
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
