import React, { useState } from 'react';

/**
 * Chapter 3: Experiments and Models
 * "Turn questions into experiments."
 * Features reproducible AutoML benchmarking, permutation feature importance,
 * and an interactive selected-feature response curve with What-If simulation.
 */
export default function ModelStoryVisual() {
  const [sliderVal, setSliderVal] = useState(65);

  const models = [
    { rank: 1, name: 'Random Forest Classifier', auc: '0.892', f1: '0.841', logLoss: '0.312', seed: '42 (5-Fold CV)', status: 'Selected Best' },
    { rank: 2, name: 'Gradient Boosted Trees', auc: '0.884', f1: '0.830', logLoss: '0.320', seed: '42 (5-Fold CV)', status: 'Candidate' },
    { rank: 3, name: 'Ridge Logistic Regression', auc: '0.865', f1: '0.810', logLoss: '0.345', seed: '42 (5-Fold CV)', status: 'Candidate' },
    { rank: 4, name: 'Decision Tree (Baseline)', auc: '0.771', f1: '0.725', logLoss: '0.490', seed: '42 (5-Fold CV)', status: 'Baseline' },
  ];

  const features = [
    { name: 'biomarker_a', imp: 34.2, color: 'var(--home-accent-cyan)' },
    { name: 'age', imp: 21.5, color: 'var(--home-accent-indigo)' },
    { name: 'treatment_arm', imp: 18.1, color: 'var(--home-accent-violet)' },
    { name: 'systolic_bp', imp: 14.2, color: '#38BDF8' },
    { name: 'cholesterol_mg_dl', imp: 12.0, color: '#818CF8' },
  ];

  // Sigmoid-based response curve calculation
  const predictedProb = Math.min(0.98, Math.max(0.04, 1 / (1 + Math.exp(-0.06 * (sliderVal - 50))))).toFixed(2);

  return (
    <section id="chapter-models" className="story-chapter">
      <div className="container">
        <div className="row align-items-center g-5">
          <div className="col-lg-5">
            <div className="chapter-eyebrow">
              <i className="bi bi-cpu"></i>
              <span>Chapter 03 — Experiments and Models</span>
            </div>

            <h2 className="chapter-title">
              Turn questions into experiments.
            </h2>

            <p className="chapter-lead">
              Automated Machine Learning tests candidate algorithms under reproducible seeded splits. Inspect model leaderboards, isolate feature importance, and explore feature response curves in real time.
            </p>

            <div className="d-flex flex-column gap-3 mb-4">
              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-trophy fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">AutoML Benchmark Leaderboard</h6>
                  <p className="small mb-0 text-muted">
                    Evaluates ensemble and linear models across stratified folds with exact AUC, F1, and log loss metrics.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-indigo">
                  <i className="bi bi-bezier2 fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Feature Response Curves</h6>
                  <p className="small mb-0 text-muted">
                    Trace how marginal changes in individual predictors influence model probabilities across the entire feature space.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-sliders fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Interactive What-If Simulation</h6>
                  <p className="small mb-0 text-muted">
                    Vary simulated patient biomarkers or experimental parameters to observe projected response shifts instantly.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="col-lg-7">
            <div className="home-card mb-4">
              <div className="home-card-header">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-trophy text-highlight-cyan"></i>
                  <span className="fw-bold text-white small">AutoML Benchmark Leaderboard</span>
                </div>
                <span className="illustrative-tag">
                  <i className="bi bi-info-circle"></i> Illustrative Data
                </span>
              </div>
              <div className="home-card-body">
                {models.map((m) => (
                  <div key={m.rank} className={`leaderboard-row ${m.rank === 1 ? 'rank-1' : ''}`}>
                    <div className="d-flex align-items-center gap-3">
                      <div className="leaderboard-rank">{m.rank}</div>
                      <div>
                        <div className="fw-bold text-white small">{m.name}</div>
                        <div className="text-muted font-monospace small" style={{ fontSize: '0.72rem' }}>Seed: {m.seed}</div>
                      </div>
                    </div>
                    <div className="d-flex align-items-center gap-3 font-monospace small">
                      <div>
                        <span className="text-muted" style={{ fontSize: '0.7rem' }}>AUC </span>
                        <strong className="text-highlight-cyan">{m.auc}</strong>
                      </div>
                      <div>
                        <span className="text-muted" style={{ fontSize: '0.7rem' }}>F1 </span>
                        <strong className="text-white">{m.f1}</strong>
                      </div>
                      <span className={`badge ${m.rank === 1 ? 'bg-success' : 'bg-dark border border-white border-opacity-10'} small`}>
                        {m.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Interactive Feature Response & What-If */}
            <div className="home-card">
              <div className="home-card-header">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-sliders text-highlight-cyan"></i>
                  <span className="fw-bold text-white small">Selected-Feature Response Curve &amp; What-If</span>
                </div>
                <span className="badge bg-primary bg-opacity-25 text-info border border-info border-opacity-50 small">
                  Target: response_score &gt; 70
                </span>
              </div>

              <div className="home-card-body">
                <div className="row g-4 align-items-center">
                  <div className="col-md-7">
                    <div className="curve-container">
                      <div className="d-flex justify-content-between align-items-center mb-2 small text-muted">
                        <span>Marginal Response: biomarker_a</span>
                        <span className="font-monospace text-highlight-cyan">P(Response)</span>
                      </div>
                      {/* SVG Feature Curve */}
                      <svg viewBox="0 0 300 120" className="w-100" style={{ height: '110px' }} aria-label="Feature response curve">
                        <defs>
                          <linearGradient id="curveGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                            <stop offset="0%" stopColor="#22D3EE" stopOpacity="0.4" />
                            <stop offset="100%" stopColor="#22D3EE" stopOpacity="0.0" />
                          </linearGradient>
                        </defs>
                        {/* Grid lines */}
                        <line x1="20" y1="20" x2="280" y2="20" stroke="rgba(255,255,255,0.08)" strokeDasharray="3 3" />
                        <line x1="20" y1="60" x2="280" y2="60" stroke="rgba(255,255,255,0.08)" strokeDasharray="3 3" />
                        <line x1="20" y1="100" x2="280" y2="100" stroke="rgba(255,255,255,0.15)" />
                        {/* Area Fill */}
                        <path
                          d="M 20 100 Q 100 100, 150 60 T 280 20 L 280 100 Z"
                          fill="url(#curveGradient)"
                        />
                        {/* Curve Line */}
                        <path
                          d="M 20 100 Q 100 100, 150 60 T 280 20"
                          fill="none"
                          stroke="var(--home-accent-cyan)"
                          strokeWidth="2.5"
                        />
                        {/* Current Value Marker */}
                        {(() => {
                          const markerX = 20 + ((sliderVal - 20) / 80) * 260;
                          const markerY = 100 - (parseFloat(predictedProb) * 80);
                          return (
                            <g>
                              <line x1={markerX} y1="20" x2={markerX} y2="100" stroke="#818CF8" strokeDasharray="2 2" />
                              <circle cx={markerX} cy={markerY} r="5" fill="#FFFFFF" stroke="#22D3EE" strokeWidth="2" />
                            </g>
                          );
                        })()}
                      </svg>
                      <div className="d-flex justify-content-between small text-muted font-monospace mt-1">
                        <span>Low (20 μg/L)</span>
                        <span>Baseline (50)</span>
                        <span>High (100 μg/L)</span>
                      </div>
                    </div>
                  </div>

                  <div className="col-md-5">
                    <div className="p-3 rounded bg-dark border border-white border-opacity-10 text-center">
                      <label htmlFor="whatif-range-slider" className="small text-muted mb-2 d-block">
                        What-If Simulator: <strong className="text-white font-monospace">{sliderVal} μg/L</strong>
                      </label>
                      <input
                        id="whatif-range-slider"
                        type="range"
                        className="whatif-slider mb-3"
                        min="20"
                        max="100"
                        value={sliderVal}
                        onChange={(e) => setSliderVal(parseInt(e.target.value, 10))}
                        aria-label="What-if biomarker simulator slider"
                      />
                      <div className="small text-muted mb-1">Predicted Favorable Response:</div>
                      <div className="fs-3 fw-bold text-highlight-cyan font-monospace">
                        {(parseFloat(predictedProb) * 100).toFixed(0)}%
                      </div>
                      <span className="badge bg-secondary bg-opacity-25 text-light mt-1 small font-monospace">
                        P = {predictedProb}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-3 pt-2 border-top border-white border-opacity-10 text-end small fst-italic text-dim">
                  Illustrative model benchmark for scientific demonstration
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
