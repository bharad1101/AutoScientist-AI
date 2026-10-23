import React from 'react';
import { Line, Scatter } from 'react-chartjs-2';
import { Chart as ChartJS } from 'chart.js';
import '../utils/chartSetup';

const num = (v) => (typeof v === 'number' && !Number.isNaN(v) ? v : null);

function StatTile({ label, value, suffix, tone }) {
  const display = value === null || value === undefined ? 'N/A' : `${value}${suffix || ''}`;
  return (
    <div className="col">
      <div className={`border rounded p-3 text-center h-100 ${tone ? `bg-${tone} bg-opacity-10` : ''}`}>
        <div className="text-muted small fw-semibold text-uppercase">{label}</div>
        <div className="fs-4 fw-bold">{display}</div>
      </div>
    </div>
  );
}

function CrossValidationCard({ cv }) {
  if (!cv) return null;
  const completed = cv.status === 'COMPLETED';
  const scores = Array.isArray(cv.scores) ? cv.scores : [];
  return (
    <div className="col-lg-6">
      <div className="card shadow-sm border-0 h-100">
        <div className="card-header bg-white py-3 border-0 d-flex justify-content-between align-items-center">
          <h6 className="fw-bold mb-0">
            <i className="bi bi-arrow-repeat text-primary me-2"></i>
            Cross-Validation
          </h6>
          <span
            className={`badge ${
              completed ? 'bg-success' : cv.status === 'FAILED' ? 'bg-danger' : 'bg-secondary'
            }`}
          >
            {cv.status || 'UNKNOWN'}
          </span>
        </div>
        <div className="card-body p-4">
          {completed ? (
            <>
              <div className="d-flex justify-content-around mb-3">
                <div className="text-center">
                  <div className="text-muted small text-uppercase fw-semibold">
                    {cv.metric ? `${cv.metric.toUpperCase()} Mean` : 'Mean'}
                  </div>
                  <div className="fs-4 fw-bold text-primary">{num(cv.mean)?.toFixed(4) ?? 'N/A'}</div>
                </div>
                <div className="text-center">
                  <div className="text-muted small text-uppercase fw-semibold">&plusmn; Std Dev</div>
                  <div className="fs-4 fw-bold">{num(cv.std)?.toFixed(4) ?? 'N/A'}</div>
                </div>
                <div className="text-center">
                  <div className="text-muted small text-uppercase fw-semibold">Folds</div>
                  <div className="fs-4 fw-bold">{cv.folds ?? scores.length}</div>
                </div>
              </div>
              <div className="small text-muted mb-1 fw-semibold">Per-fold scores</div>
              <div className="d-flex flex-wrap gap-1">
                {scores.map((s, i) => (
                  <span key={i} className="badge bg-light text-dark border">
                    Fold {i + 1}: {typeof s === 'number' ? s.toFixed(4) : s}
                  </span>
                ))}
              </div>
            </>
          ) : (
            <div className="text-muted small">
              <i className="bi bi-info-circle me-1"></i>
              {cv.reason || 'Cross-validation did not run for this model.'}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function ConfusionMatrix({ cm }) {
  if (!cm) return null;
  const rows = cm.rows || [];
  const rowLabels = cm.rowLabels || [];
  const colLabels = cm.columnLabels || [];
  const flat = rows.flat().filter((v) => typeof v === 'number');
  const max = flat.length ? Math.max(...flat) : 1;
  return (
    <div className="card-body p-4">
      <table className="table table-bordered align-middle text-center mb-2">
        <thead>
          <tr>
            <th className="bg-light small text-muted">Actual \ Predicted</th>
            {colLabels.map((c) => (
              <th key={c} className="bg-light small text-muted text-nowrap">
                {c.replace(/_/g, ' ')}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, ri) => (
            <tr key={ri}>
              <th className="bg-light small text-muted text-nowrap">
                {(rowLabels[ri] || `row ${ri + 1}`).replace(/_/g, ' ')}
              </th>
              {row.map((cell, ci) => {
                const intensity = max > 0 ? cell / max : 0;
                return (
                  <td
                    key={ci}
                    style={{ backgroundColor: `rgba(59, 130, 246, ${0.12 + intensity * 0.6})` }}
                    className="fw-bold fs-5"
                  >
                    {cell}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
      <div className="d-flex flex-wrap gap-3 small text-muted">
        <span>
          TP: <strong>{cm.truePositives}</strong>
        </span>
        <span>
          TN: <strong>{cm.trueNegatives}</strong>
        </span>
        <span>
          FP: <strong>{cm.falsePositives}</strong>
        </span>
        <span>
          FN: <strong>{cm.falseNegatives}</strong>
        </span>
      </div>
    </div>
  );
}

function CurveCard({ title, subtitle, dataset, xLabel, yLabel, diagonal, badge }) {
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: !!dataset.datasets && dataset.datasets.length > 1, position: 'bottom' },
      tooltip: {
        callbacks: {
          label: (ctx) =>
            `${ctx.dataset.label || ''}: (${ctx.parsed.x?.toFixed(3)}, ${ctx.parsed.y?.toFixed(3)})`,
        },
      },
    },
    scales: {
      x: { type: 'linear', min: 0, max: 1, title: { display: true, text: xLabel } },
      y: { min: 0, max: 1, title: { display: true, text: yLabel } },
    },
  };
  return (
    <div className="col-lg-6">
      <div className="card shadow-sm border-0 h-100">
        <div className="card-header bg-white py-3 border-0 d-flex justify-content-between align-items-center">
          <div>
            <h6 className="fw-bold mb-0">{title}</h6>
            {subtitle && <small className="text-muted">{subtitle}</small>}
          </div>
          {badge}
        </div>
        <div className="card-body p-3">
          <div style={{ height: '300px' }}>
            <Line data={dataset} options={options} />
          </div>
        </div>
      </div>
    </div>
  );
}

function CalibrationCard({ calibration }) {
  if (!calibration || !Array.isArray(calibration.bins) || calibration.bins.length === 0) {
    return (
      <div className="alert alert-secondary mb-4">
        <i className="bi bi-info-circle me-1"></i>
        Calibration diagnostics are unavailable because this algorithm does not expose continuous scores.
      </div>
    );
  }
  const data = {
    datasets: [
      {
        label: 'Observed vs. predicted',
        data: calibration.bins.map((b) => ({ x: b.meanPredicted, y: b.observedFrequency })),
        borderColor: 'rgb(16, 185, 129)',
        backgroundColor: 'rgba(16, 185, 129, 0.7)',
        showLine: true,
        pointRadius: 4,
        tension: 0.1,
      },
      {
        label: 'Perfect calibration',
        data: [
          { x: 0, y: 0 },
          { x: 1, y: 1 },
        ],
        borderColor: 'rgba(120, 120, 120, 0.7)',
        borderDash: [6, 4],
        borderWidth: 1,
        pointRadius: 0,
      },
    ],
  };
  return (
    <div className="card border mb-4">
      <div className="card-header bg-white border-0 py-3 d-flex justify-content-between align-items-center">
        <div>
          <h6 className="fw-bold mb-0">
            <i className="bi bi-bullseye text-primary me-2"></i>
            Probability Calibration (Reliability Diagram)
          </h6>
          <small className="text-muted">Observed positive rate versus mean predicted probability per decile.</small>
        </div>
        {num(calibration.brierScore) !== null && (
          <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25">
            Brier score: {calibration.brierScore}
          </span>
        )}
      </div>
      <div className="card-body p-3">
        <div style={{ height: '300px' }}>
          <Scatter
            data={data}
            options={{
              responsive: true,
              maintainAspectRatio: false,
              plugins: { legend: { position: 'bottom' } },
              scales: {
                x: { type: 'linear', min: 0, max: 1, title: { display: true, text: 'Mean predicted probability' } },
                y: { min: 0, max: 1, title: { display: true, text: 'Observed frequency' } },
              },
            }}
          />
        </div>
      </div>
    </div>
  );
}

function ResidualPlot({ points }) {
  if (!Array.isArray(points) || points.length === 0) {
    return (
      <div className="alert alert-secondary mb-4">
        <i className="bi bi-info-circle me-1"></i>
        Residual points are unavailable for this model.
      </div>
    );
  }
  return (
    <div className="card border mb-4">
      <div className="card-header bg-white border-0 py-3">
        <h6 className="fw-bold mb-0">
          <i className="bi bi-scatter-chart text-primary me-2"></i>
          Residual Diagnostics
        </h6>
        <small className="text-muted">Predicted minus actual value against the actual target (holdout rows, downsampled).</small>
      </div>
      <div className="card-body p-3">
        <div style={{ height: '300px' }}>
          <Scatter
            data={{
              datasets: [
                {
                  label: 'Residual',
                  data: points.map((p) => ({ x: p[0], y: p[1] })),
                  backgroundColor: 'rgba(37, 99, 235, 0.55)',
                  pointRadius: 3,
                },
              ],
            }}
            options={{
              responsive: true,
              maintainAspectRatio: false,
              plugins: { legend: { display: false } },
              scales: {
                x: { title: { display: true, text: 'Actual value' } },
                y: { title: { display: true, text: 'Residual (predicted - actual)' } },
              },
            }}
          />
        </div>
      </div>
    </div>
  );
}

export default function ModelEvaluationPanel({ evaluation, problemType, modelName }) {
  if (!evaluation || Object.keys(evaluation).length === 0) {
    return (
      <div className="card shadow-sm border-0 mb-4">
        <div className="card-header bg-white py-3 border-0">
          <h5 className="fw-bold mb-0">
            <i className="bi bi-graph-up-arrow text-primary me-2"></i>
            Model Evaluation {modelName ? `— ${modelName}` : ''}
          </h5>
        </div>
        <div className="card-body p-4 text-muted small">
          <i className="bi bi-info-circle me-1"></i>
          No persisted evaluation payload is available for this model.
        </div>
      </div>
    );
  }

  if (evaluation.status === 'UNAVAILABLE') {
    return (
      <div className="card shadow-sm border-0 mb-4">
        <div className="card-header bg-white py-3 border-0">
          <h5 className="fw-bold mb-0">
            <i className="bi bi-graph-up-arrow text-primary me-2"></i>
            Model Evaluation {modelName ? `— ${modelName}` : ''}
          </h5>
        </div>
        <div className="card-body p-4">
          <div className="alert alert-warning mb-0">
            <i className="bi bi-exclamation-triangle me-1"></i>
            Evaluation unavailable: {evaluation.reason || 'unknown reason'}
          </div>
        </div>
      </div>
    );
  }

  const isClassification = problemType === 'CLASSIFICATION';
  const cv = evaluation.crossValidation;
  const residual = evaluation.residualSummary;

  return (
    <div className="card shadow-sm border-0 mb-4">
      <div className="card-header bg-white py-3 border-0">
        <h5 className="fw-bold mb-0">
          <i className="bi bi-graph-up-arrow text-primary me-2"></i>
          Deep Model Evaluation {modelName ? `— ${modelName}` : ''}
        </h5>
        <small className="text-muted">
          {isClassification
            ? 'Holdout confusion matrix, ROC/PR curves, per-class metrics and seeded k-fold cross-validation.'
            : 'Holdout residual diagnostics and seeded k-fold cross-validation.'}
        </small>
      </div>

      {isClassification ? (
        <div className="card-body p-4">
          <div className="row g-4 mb-4">
            <div className="col-lg-6">
              <div className="card border h-100">
                <div className="card-header bg-white border-0 py-3">
                  <h6 className="fw-bold mb-0">
                    <i className="bi bi-grid-3x3-gap text-primary me-2"></i>
                    Confusion Matrix (Holdout Test)
                  </h6>
                </div>
                <ConfusionMatrix cm={evaluation.confusionMatrix} />
              </div>
            </div>
            <div className="col-lg-6">
              <div className="card border h-100">
                <div className="card-header bg-white border-0 py-3 d-flex justify-content-between align-items-center">
                  <h6 className="fw-bold mb-0">
                    <i className="bi bi-list-columns-reverse text-primary me-2"></i>
                    Per-Class Metrics
                  </h6>
                  {num(evaluation.macroF1) !== null && (
                    <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25">
                      Macro F1: {evaluation.macroF1}
                    </span>
                  )}
                </div>
                <div className="card-body p-0">
                  <div className="table-responsive">
                    <table className="table table-hover align-middle mb-0">
                      <thead className="table-light">
                        <tr>
                          <th>Class</th>
                          <th>Precision</th>
                          <th>Recall</th>
                          <th>F1</th>
                          <th>Support</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(evaluation.perClass || []).map((pc, i) => (
                          <tr key={i}>
                            <td className="fw-semibold">{pc.label}</td>
                            <td>{num(pc.precision)?.toFixed(4) ?? 'N/A'}</td>
                            <td>{num(pc.recall)?.toFixed(4) ?? 'N/A'}</td>
                            <td>{num(pc.f1)?.toFixed(4) ?? 'N/A'}</td>
                            <td>{pc.support ?? 'N/A'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {evaluation.rocStatus === 'UNAVAILABLE_NO_SCORES' ? (
            <div className="alert alert-secondary">
              <i className="bi bi-info-circle me-1"></i>
              ROC/PR curves unavailable: the selected algorithm does not expose continuous scores.
            </div>
          ) : (
            <div className="row g-4 mb-4">
              <CurveCard
                title="ROC Curve"
                subtitle="True Positive Rate vs. False Positive Rate"
                xLabel="False Positive Rate"
                yLabel="True Positive Rate"
                badge={
                  num(evaluation.auc) !== null && (
                    <span className="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25">
                      AUC: {evaluation.auc}
                    </span>
                  )
                }
                dataset={{
                  datasets: [
                    {
                      label: 'ROC',
                      data: (evaluation.rocCurve || []).map((p) => ({ x: p[0], y: p[1] })),
                      borderColor: 'rgb(37, 99, 235)',
                      backgroundColor: 'rgba(59, 130, 246, 0.15)',
                      borderWidth: 2,
                      pointRadius: 0,
                      tension: 0.1,
                      fill: true,
                    },
                    {
                      label: 'Chance',
                      data: [
                        { x: 0, y: 0 },
                        { x: 1, y: 1 },
                      ],
                      borderColor: 'rgba(120, 120, 120, 0.6)',
                      borderDash: [6, 4],
                      borderWidth: 1,
                      pointRadius: 0,
                    },
                  ],
                }}
              />
              <CurveCard
                title="Precision-Recall Curve"
                subtitle="Precision vs. Recall"
                xLabel="Recall"
                yLabel="Precision"
                badge={
                  num(evaluation.averagePrecision) !== null && (
                    <span className="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25">
                      Avg Precision: {evaluation.averagePrecision}
                    </span>
                  )
                }
                dataset={{
                  datasets: [
                    {
                      label: 'PR',
                      data: (evaluation.prCurve || []).map((p) => ({ x: p[0], y: p[1] })),
                      borderColor: 'rgb(217, 70, 239)',
                      backgroundColor: 'rgba(217, 70, 239, 0.15)',
                      borderWidth: 2,
                      pointRadius: 0,
                      tension: 0.1,
                      fill: true,
                    },
                  ],
                }}
              />
            </div>
          )}

          <CalibrationCard calibration={evaluation.calibration} />

          <div className="row g-4">
            <CrossValidationCard cv={cv} />
          </div>
        </div>
      ) : (
        <div className="card-body p-4">
          <div className="row row-cols-2 row-cols-lg-4 g-3 mb-4">
            <StatTile label="Mean Error (bias)" value={num(residual?.meanError)} />
            <StatTile label="MAE" value={num(residual?.mae)} />
            <StatTile label="Max Abs Error" value={num(residual?.maxAbsError)} />
            <StatTile label="Test Rows" value={num(residual?.n)} />
          </div>
          <ResidualPlot points={evaluation.residualPoints} />
          <div className="row g-4">
            <CrossValidationCard cv={cv} />
          </div>
        </div>
      )}
    </div>
  );
}
