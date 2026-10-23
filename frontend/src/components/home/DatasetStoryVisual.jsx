import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * Chapter 1: Raw Data
 * "Every discovery starts with data."
 * Showcases data ingestion, schema profiling, identifier isolation,
 * and a 100-point data quality score.
 */
export default function DatasetStoryVisual({ isAuthenticated }) {
  const navigate = useNavigate();
  const [filterActive, setFilterActive] = useState('all');

  const sampleRows = [
    { id: 'PT-10491', age: 54, chol: 218, arm: 'Arm A (Active)', bp: 128, score: 78.4 },
    { id: 'PT-10492', age: 62, chol: 245, arm: 'Arm B (High Dose)', bp: 138, score: 89.2 },
    { id: 'PT-10493', age: 48, chol: 192, arm: 'Control (Placebo)', bp: 122, score: 52.1 },
    { id: 'PT-10494', age: 59, chol: 230, arm: 'Arm A (Active)', bp: 134, score: 74.8 },
    { id: 'PT-10495', age: 67, chol: 260, arm: 'Arm B (High Dose)', bp: 142, score: 91.5 },
  ];

  return (
    <section id="chapter-raw-data" className="story-chapter">
      <div className="container">
        <div className="row align-items-center g-5">
          <div className="col-lg-5">
            <div className="chapter-eyebrow">
              <i className="bi bi-database-check"></i>
              <span>Chapter 01 — Raw Data</span>
            </div>

            <h2 className="chapter-title">
              Every discovery starts with data.
            </h2>

            <p className="chapter-lead">
              Upload raw CSV or Excel workbooks and let AutoScientist AI perform rigorous schema profiling, isolation of identifier columns, and automated 100-point data quality auditing.
            </p>

            <div className="d-flex flex-column gap-3 mb-4">
              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-file-earmark-spreadsheet fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Guided Ingestion Wizard</h6>
                  <p className="small mb-0 text-muted">
                    Supports CSV, TSV, and multi-sheet Excel files. Automatically detects header rows, delimiter encoding, and column data types with non-destructive override controls.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-indigo">
                  <i className="bi bi-shield-lock fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Automated Identifier Isolation</h6>
                  <p className="small mb-0 text-muted">
                    Unique identifiers like <code>patient_id</code> or <code>subject_uuid</code> are automatically detected and quarantined so they never leak into correlations or predictive models.
                  </p>
                </div>
              </div>

              <div className="d-flex align-items-start gap-3">
                <div className="p-2 rounded bg-dark border border-white border-opacity-10 text-highlight-cyan">
                  <i className="bi bi-check2-circle fs-5"></i>
                </div>
                <div>
                  <h6 className="fw-bold mb-1 text-white">Non-Destructive Versioning</h6>
                  <p className="small mb-0 text-muted">
                    Original data files are never altered or rewritten. Exclusions and transformations are tracked as reproducible audit parameters.
                  </p>
                </div>
              </div>
            </div>

            <div className="d-flex align-items-center gap-3">
              <button
                className="btn-discovery-primary"
                onClick={() => navigate(isAuthenticated ? '/datasets' : '/register')}
              >
                <i className="bi bi-cloud-arrow-up"></i>
                <span>Upload a Dataset</span>
              </button>
            </div>
          </div>

          <div className="col-lg-7">
            <div className="home-card">
              <div className="home-card-header">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-table text-highlight-cyan"></i>
                  <span className="fw-bold text-white small">cohort_biomarkers.xlsx</span>
                  <span className="badge bg-secondary bg-opacity-25 text-light border border-white border-opacity-10 small">Sheet: Clinical_Trial</span>
                </div>
                <span className="illustrative-tag">
                  <i className="bi bi-info-circle"></i> Illustrative Data
                </span>
              </div>

              <div className="home-card-body">
                {/* Quality Score Bar */}
                <div className="quality-score-box mb-3">
                  <div className="score-number">98.4</div>
                  <div>
                    <div className="d-flex align-items-center gap-2">
                      <span className="fw-bold text-white">Data Quality Score</span>
                      <span className="badge bg-success bg-opacity-25 text-success border border-success border-opacity-25">Passed (High Quality)</span>
                    </div>
                    <div className="small text-muted mt-1">
                      0 duplicate rows · 0 missing critical values · 0 high-leverage outliers · Identifiers isolated
                    </div>
                  </div>
                </div>

                {/* Table Preview */}
                <div className="preview-table-container">
                  <div className="table-responsive">
                    <table className="preview-table">
                      <thead>
                        <tr>
                          <th>
                            patient_id
                            <span className="col-badge-id" title="Quarantined from model features">ID Isolated</span>
                          </th>
                          <th>
                            age
                            <span className="col-badge-num">Numeric</span>
                          </th>
                          <th>
                            cholesterol_mg_dl
                            <span className="col-badge-num">Numeric</span>
                          </th>
                          <th>
                            treatment_arm
                            <span className="col-badge-cat">Categorical</span>
                          </th>
                          <th>
                            systolic_bp
                            <span className="col-badge-num">Numeric</span>
                          </th>
                          <th>
                            response_score
                            <span className="col-badge-num">Target</span>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {sampleRows.map((row) => (
                          <tr key={row.id}>
                            <td className="font-monospace text-warning">{row.id}</td>
                            <td>{row.age}</td>
                            <td>{row.chol}</td>
                            <td>
                              <span className="badge bg-dark border border-white border-opacity-10 text-light">
                                {row.arm}
                              </span>
                            </td>
                            <td>{row.bp}</td>
                            <td className="fw-bold text-highlight-cyan">{row.score}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div className="d-flex justify-content-between align-items-center mt-3 pt-2 border-top border-white border-opacity-10 small text-muted">
                  <span>Showing 5 of 5,412 profiled records</span>
                  <span className="fst-italic text-dim">Illustrative benchmark dataset for scientific demonstration</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
