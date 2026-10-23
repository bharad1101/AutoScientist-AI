import React, { useState, useEffect, useMemo } from 'react';
import { Line, Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Tooltip,
  Legend,
} from 'chart.js';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';
import PrerequisiteAlert from '../components/PrerequisiteAlert';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, BarElement, Tooltip, Legend);

export default function DriftPage() {
  const [datasets, setDatasets] = useState([]);
  const [referenceId, setReferenceId] = useState('');
  const [targetId, setTargetId] = useState('');
  const [loading, setLoading] = useState(false);
  const [driftReport, setDriftReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadDatasets();
  }, []);

  const loadDatasets = async () => {
    try {
      const res = await datasetService.getDatasets();
      if (res.data && res.data.length >= 1) {
        setDatasets(res.data);
        setReferenceId(res.data[0].id);
        if (res.data.length > 1) {
          setTargetId(res.data[1].id);
        } else {
          setTargetId(res.data[0].id);
        }
      }
    } catch (err) {
      setError('Failed to load datasets.');
    }
  };

  const handleRunDrift = async (e) => {
    e.preventDefault();
    if (!referenceId || !targetId) return;

    setLoading(true);
    setError(null);
    try {
      const res = await analysisService.compareDrift(referenceId, targetId);
      setDriftReport(res.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Drift analysis failed.');
    } finally {
      setLoading(false);
    }
  };

  // Most drifted numeric feature drives the distribution comparison chart.
  const numericDrifts = useMemo(
    () => (driftReport?.columnDrifts || []).filter((c) => c.dataType === 'NUMERIC' && c.binEdges?.length),
    [driftReport]
  );

  const [selectedDriftColumn, setSelectedDriftColumn] = useState('');

  useEffect(() => {
    if (!numericDrifts.length) {
      setSelectedDriftColumn('');
      return;
    }
    const stillPresent = numericDrifts.find((c) => c.columnName === selectedDriftColumn);
    if (!stillPresent) {
      const worst = [...numericDrifts].sort((a, b) => (b.psiScore || 0) - (a.psiScore || 0))[0];
      setSelectedDriftColumn(worst.columnName);
    }
  }, [numericDrifts, selectedDriftColumn]);

  const activeDrift = numericDrifts.find((c) => c.columnName === selectedDriftColumn) || null;

  const driftDistributionData = useMemo(() => {
    if (!activeDrift) return null;
    const edges = activeDrift.binEdges || [];
    const labels = edges.slice(0, -1).map((start, i) => `${start}–${edges[i + 1]}`);
    return {
      labels,
      datasets: [
        {
          label: 'Reference share',
          data: (activeDrift.referenceBinProportions || []).map((p) => p * 100),
          borderColor: 'rgba(37, 99, 235, 1)',
          backgroundColor: 'rgba(37, 99, 235, 0.15)',
          borderWidth: 2,
          pointRadius: 2,
          tension: 0.2,
          fill: false,
        },
        {
          label: 'Target share',
          data: (activeDrift.targetBinProportions || []).map((p) => p * 100),
          borderColor: 'rgba(239, 68, 68, 1)',
          backgroundColor: 'rgba(239, 68, 68, 0.15)',
          borderWidth: 2,
          borderDash: [6, 4],
          pointRadius: 2,
          tension: 0.2,
          fill: false,
        },
      ],
    };
  }, [activeDrift]);

  const psiChartData = useMemo(() => {
    const rows = driftReport?.columnDrifts || [];
    if (!rows.length) return null;
    return {
      labels: rows.map((c) => c.columnName),
      datasets: [
        {
          label: 'PSI',
          data: rows.map((c) => c.psiScore || 0),
          backgroundColor: rows.map((c) =>
            c.psiScore >= 0.25 ? 'rgba(239, 68, 68, 0.8)' : c.psiScore >= 0.1 ? 'rgba(245, 158, 11, 0.8)' : 'rgba(16, 185, 129, 0.8)'
          ),
          borderRadius: 3,
        },
      ],
    };
  }, [driftReport]);

  return (
    <div className="container-fluid py-4 px-4">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="fw-bold mb-1">
            <i className="bi bi-water text-primary me-2"></i>
            Data Drift & Cohort Stability Engine
          </h2>
          <p className="text-muted mb-0">
            Calculate Population Stability Index (PSI), distribution shift, and Welch-t tests across datasets.
          </p>
        </div>
      </div>

      <PrerequisiteAlert stageKey="ml" />

      {datasets.length === 1 && (
        <div className="alert alert-info d-flex align-items-start gap-2" role="alert">
          <i className="bi bi-info-circle-fill mt-1"></i>
          <div>
            <strong>Drift needs two cohorts.</strong> Only one dataset is available. Upload a comparable cohort to
            measure real distribution shift instead of comparing a dataset with itself.
          </div>
        </div>
      )}

      {error && (
        <div className="alert alert-danger alert-dismissible fade show" role="alert">
          <i className="bi bi-exclamation-triangle-fill me-2"></i>
          {error}
          <button type="button" className="btn-close" onClick={() => setError(null)}></button>
        </div>
      )}

      {/* Dataset Selection */}
      <div className="card shadow-sm border-0 mb-4">
        <div className="card-body p-4">
          <form onSubmit={handleRunDrift}>
            <div className="row g-3 align-items-end">
              <div className="col-md-5">
                <label className="form-label fw-semibold">Baseline / Reference Dataset</label>
                <select
                  className="form-select"
                  value={referenceId}
                  onChange={(e) => setReferenceId(e.target.value)}
                  disabled={loading}
                >
                  {datasets.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name} ({d.rowCount} rows)
                    </option>
                  ))}
                </select>
              </div>

              <div className="col-md-5">
                <label className="form-label fw-semibold">Comparison / Target Dataset</label>
                <select
                  className="form-select"
                  value={targetId}
                  onChange={(e) => setTargetId(e.target.value)}
                  disabled={loading}
                >
                  {datasets.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name} ({d.rowCount} rows)
                    </option>
                  ))}
                </select>
              </div>

              <div className="col-md-2">
                <button
                  type="submit"
                  className="btn btn-primary w-100 py-2 fw-semibold"
                  disabled={loading || !referenceId || !targetId}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                      Evaluating...
                    </>
                  ) : (
                    <>
                      <i className="bi bi-speedometer2 me-2"></i>
                      Analyze Drift
                    </>
                  )}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>

      {/* Drift Report */}
      {driftReport && (
        <>
          {/* Summary Overview Cards */}
          <div className="row g-4 mb-4">
            <div className="col-md-4">
              <div className="card shadow-sm border-0 p-3 text-center h-100">
                <span className="text-muted small fw-semibold">Overall Population Stability Index (PSI)</span>
                <h2 className="fw-bold my-2 text-primary">
                  {driftReport.overallDriftIndex?.toFixed(4)}
                </h2>
                <div>
                  <span
                    className={`badge ${
                      driftReport.overallStatus === 'STABLE'
                        ? 'bg-success'
                        : driftReport.overallStatus === 'LOW_RISK'
                        ? 'bg-warning text-dark'
                        : 'bg-danger'
                    } px-3 py-2`}
                  >
                    {driftReport.overallStatus}
                  </span>
                </div>
              </div>
            </div>

            <div className="col-md-4">
              <div className="card shadow-sm border-0 p-3 text-center h-100">
                <span className="text-muted small fw-semibold">Features Evaluated</span>
                <h2 className="fw-bold my-2 text-dark">
                  {driftReport.totalFeaturesAnalyzed}
                </h2>
                <small className="text-muted">
                  Common aligned columns across both cohorts
                </small>
              </div>
            </div>

            <div className="col-md-4">
              <div className="card shadow-sm border-0 p-3 text-center h-100">
                <span className="text-muted small fw-semibold">Drifted Feature Count</span>
                <h2
                  className={`fw-bold my-2 ${
                    driftReport.driftedFeaturesCount > 0 ? 'text-danger' : 'text-success'
                  }`}
                >
                  {driftReport.driftedFeaturesCount}
                </h2>
                <small className="text-muted">Features with PSI &ge; 0.10</small>
              </div>
            </div>
          </div>

          {/* Recommendation Banner */}
          <div className="alert alert-light border shadow-sm p-4 mb-4">
            <h5 className="alert-heading fw-bold mb-2">
              <i className="bi bi-info-circle-fill text-primary me-2"></i>
              System Recommendation & Stability Diagnosis
            </h5>
            <p className="mb-0 fs-6">{driftReport.summaryRecommendation}</p>
          </div>

          {/* Per-Feature PSI Overview */}
          {psiChartData && (
            <div className="card shadow-sm border-0 mb-4">
              <div className="card-header bg-white py-3 border-0">
                <h5 className="fw-bold mb-0">Population Stability Index by Feature</h5>
                <small className="text-muted">
                  PSI &ge; 0.25 indicates significant drift, &ge; 0.10 moderate drift.
                </small>
              </div>
              <div className="card-body">
                <div style={{ height: '260px' }}>
                  <Bar
                    data={psiChartData}
                    options={{
                      responsive: true,
                      maintainAspectRatio: false,
                      plugins: { legend: { display: false } },
                      scales: {
                        x: { title: { display: true, text: 'Feature' } },
                        y: { beginAtZero: true, title: { display: true, text: 'PSI' } },
                      },
                    }}
                  />
                </div>
              </div>
            </div>
          )}

          {/* Reference vs Target Distribution Comparison */}
          {numericDrifts.length > 0 && (
            <div className="card shadow-sm border-0 mb-4">
              <div className="card-header bg-white py-3 border-0 d-flex flex-wrap justify-content-between align-items-center gap-2">
                <div>
                  <h5 className="fw-bold mb-0">Reference vs Target Distribution</h5>
                  <small className="text-muted">
                    Aligned bins computed on the reference cohort — the same bins the PSI is computed on.
                  </small>
                </div>
                <select
                  className="form-select form-select-sm w-auto"
                  value={selectedDriftColumn}
                  onChange={(e) => setSelectedDriftColumn(e.target.value)}
                  aria-label="Select the feature to compare distributions for"
                >
                  {numericDrifts.map((c) => (
                    <option key={c.columnName} value={c.columnName}>
                      {c.columnName} (PSI {Number(c.psiScore).toFixed(3)})
                    </option>
                  ))}
                </select>
              </div>
              <div className="card-body">
                {driftDistributionData ? (
                  <>
                    <div style={{ height: '320px' }}>
                      <Line
                        data={driftDistributionData}
                        options={{
                          responsive: true,
                          maintainAspectRatio: false,
                          plugins: { legend: { position: 'bottom' } },
                          scales: {
                            x: { title: { display: true, text: activeDrift?.columnName } },
                            y: { beginAtZero: true, title: { display: true, text: 'Share of cohort (%)' } },
                          },
                        }}
                      />
                    </div>
                    <div className="row g-2 mt-1 small text-muted">
                      <div className="col-md-3">
                        PSI <span className="fw-semibold text-dark">{Number(activeDrift?.psiScore).toFixed(4)}</span>
                      </div>
                      <div className="col-md-3">
                        KS D <span className="fw-semibold text-dark">{activeDrift?.ksStatistic != null ? Number(activeDrift.ksStatistic).toFixed(4) : '—'}</span>
                      </div>
                      <div className="col-md-3">
                        KS p <span className="fw-semibold text-dark">{activeDrift?.ksPValue != null ? Number(activeDrift.ksPValue).toFixed(4) : '—'}</span>
                      </div>
                      <div className="col-md-3">
                        Mean shift{' '}
                        <span className="fw-semibold text-dark">
                          {activeDrift?.meanShiftPct != null ? `${activeDrift.meanShiftPct.toFixed(1)}%` : '—'}
                        </span>
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="p-4 text-center bg-light rounded border">
                    <p className="text-muted small mb-0">No binned distribution is available for this feature.</p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Per-Column Drift Breakdown Table */}
          <div className="card shadow-sm border-0 mb-4">
            <div className="card-header bg-white py-3 border-0">
              <h5 className="fw-bold mb-0">Feature-Level Distribution Shift Breakdown</h5>
            </div>
            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0">
                  <thead className="table-light">
                    <tr>
                      <th>Feature Name</th>
                      <th>Type</th>
                      <th>PSI Score</th>
                      <th>Drift Status</th>
                      <th>Ref Mean</th>
                      <th>Target Mean</th>
                      <th>Mean Shift</th>
                      <th>Empirical Interpretation</th>
                    </tr>
                  </thead>
                  <tbody>
                    {driftReport.columnDrifts?.map((c) => (
                      <tr key={c.columnName}>
                        <td className="fw-semibold text-primary">{c.columnName}</td>
                        <td>
                          <span className="badge bg-light text-dark border">{c.dataType}</span>
                        </td>
                        <td className="fw-bold">{c.psiScore?.toFixed(4)}</td>
                        <td>
                          <span
                            className={`badge ${
                              c.driftStatus === 'NO_DRIFT'
                                ? 'bg-success'
                                : c.driftStatus === 'MODERATE_DRIFT'
                                ? 'bg-warning text-dark'
                                : 'bg-danger'
                            }`}
                          >
                            {c.driftStatus}
                          </span>
                        </td>
                        <td>{c.referenceMean != null ? c.referenceMean.toFixed(2) : '-'}</td>
                        <td>{c.targetMean != null ? c.targetMean.toFixed(2) : '-'}</td>
                        <td>
                          {c.meanShiftPct != null ? (
                            <span
                              className={
                                Math.abs(c.meanShiftPct) > 10 ? 'text-danger fw-semibold' : 'text-muted'
                              }
                            >
                              {c.meanShiftPct > 0 ? '+' : ''}
                              {c.meanShiftPct.toFixed(1)}%
                            </span>
                          ) : (
                            '-'
                          )}
                        </td>
                        <td>
                          <small className="text-muted">{c.interpretation}</small>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
