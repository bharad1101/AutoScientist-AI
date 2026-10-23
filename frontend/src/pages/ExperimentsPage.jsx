import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { researchService } from '../services/researchService';
import PrerequisiteAlert from '../components/PrerequisiteAlert';

const ExperimentsPage = () => {
  const [experiments, setExperiments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [comparison, setComparison] = useState(null);
  const [comparisonLoading, setComparisonLoading] = useState(false);
  const [comparisonError, setComparisonError] = useState('');

  const handleCompare = async (experimentId) => {
    try {
      setComparisonLoading(true);
      setComparisonError('');
      const res = await researchService.getExperimentComparison(experimentId);
      setComparison(res.data);
    } catch (err) {
      setComparisonError(err.response?.data?.message || 'Failed to load version comparison.');
    } finally {
      setComparisonLoading(false);
    }
  };

  const metricKeys = comparison
    ? Array.from(
        new Set(
          (comparison.versions || []).flatMap((v) =>
            (v.models || []).flatMap((m) => Object.keys(m.testMetrics || {}))
          )
        )
      )
    : [];

  useEffect(() => {
    const loadExperiments = async () => {
      try {
        setLoading(true);
        setError('');
        const res = await researchService.listExperiments();
        if (res && res.data) setExperiments(res.data);
      } catch (err) {
        setError(err.response?.data?.message || 'Failed to load experiments.');
      } finally {
        setLoading(false);
      }
    };

    loadExperiments();
  }, []);

  return (
    <div className="container py-4">
      <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
        <div>
          <h2 className="fw-bold mb-1">
            <i className="bi bi-kanban-fill text-primary me-2"></i>Machine Learning Experiments
          </h2>
          <p className="text-muted small mb-0">
            Reproducible experiment runs, preprocessing configurations, and versioned AutoML benchmarks.
          </p>
        </div>
      </div>

      <PrerequisiteAlert stageKey="ml" />

      {comparisonLoading && (
        <div className="alert alert-info d-flex align-items-center gap-2">
          <span className="spinner-border spinner-border-sm"></span>
          Loading cross-version comparison...
        </div>
      )}

      {comparisonError && (
        <div className="alert alert-danger d-flex justify-content-between align-items-center">
          <span><i className="bi bi-exclamation-triangle-fill me-2"></i>{comparisonError}</span>
          <button className="btn btn-sm btn-outline-danger" onClick={() => setComparisonError('')}>Dismiss</button>
        </div>
      )}

      {comparison && (
        <div className="card shadow-sm border-0 mb-4">
          <div className="card-header bg-white py-3 d-flex justify-content-between align-items-start flex-wrap gap-2">
            <div>
              <h5 className="fw-bold mb-1">
                <i className="bi bi-bar-chart-steps text-primary me-2"></i>
                Version Comparison — {comparison.experimentName}
              </h5>
              <small className="text-muted">
                {comparison.datasetName} &bull; target: <strong>{comparison.targetColumn}</strong> &bull; {comparison.problemType}
              </small>
            </div>
            <button className="btn btn-sm btn-outline-secondary" onClick={() => setComparison(null)}>
              <i className="bi bi-x-lg me-1"></i>Close
            </button>
          </div>
          <div className="card-body p-0">
            <div className="table-responsive">
              <table className="table table-sm table-hover align-middle mb-0">
                <thead className="table-light">
                  <tr>
                    <th>Version</th>
                    <th>Model</th>
                    <th>Best</th>
                    <th>Dataset v</th>
                    <th>Seed</th>
                    <th>Engine</th>
                    {metricKeys.map((k) => <th key={k} className="text-nowrap">Test {k}</th>)}
                  </tr>
                </thead>
                <tbody>
                  {(comparison.versions || []).flatMap((v) =>
                    (v.models || []).map((m) => (
                      <tr key={`${v.versionId}-${m.modelRunId}`}>
                        <td><span className="badge bg-primary bg-opacity-10 text-primary">v{v.versionNumber}</span></td>
                        <td className="fw-semibold">{m.modelName}</td>
                        <td>{m.best ? <i className="bi bi-star-fill text-warning"></i> : <span className="text-muted">—</span>}</td>
                        <td>{v.datasetVersion ?? '—'}</td>
                        <td>{v.randomSeed ?? '—'}</td>
                        <td><span className="small text-muted">{v.engineVersion || '—'}</span></td>
                        {metricKeys.map((k) => (
                          <td key={k} className="text-nowrap">
                            {typeof m.testMetrics?.[k] === 'number' ? m.testMetrics[k].toFixed(4) : '—'}
                          </td>
                        ))}
                      </tr>
                    ))
                  )}
                  {(comparison.versions || []).every((v) => !v.models || v.models.length === 0) && (
                    <tr>
                      <td colSpan={6 + metricKeys.length} className="text-center text-muted py-4">
                        No model runs are attached to these versions.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="alert alert-danger" role="alert">
          <i className="bi bi-exclamation-triangle-fill me-2"></i>
          {error}
        </div>
      )}

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
          <p className="text-muted small mt-2">Loading reproducible experiment history...</p>
        </div>
      ) : experiments.length === 0 ? (
        <div className="card text-center py-5 border-dashed bg-white">
          <div className="card-body">
            <i className="bi bi-cpu display-4 text-muted mb-2 d-block"></i>
            <h5 className="fw-bold">No ML Experiments Tracked Yet</h5>
            <p className="text-muted small max-w-md mx-auto">
              Train AutoML models on any dataset to automatically register versioned benchmarks with complete parameter snapshots.
            </p>
            <Link to="/datasets" className="btn btn-outline-primary mt-2">
              <i className="bi bi-database me-1"></i> Browse Datasets
            </Link>
          </div>
        </div>
      ) : (
        <div className="row g-4">
          {experiments.map((exp) => (
            <div key={exp.id} className="col-12">
              <div className="card shadow-sm border-0 p-4 bg-white">
                <div className="d-flex justify-content-between align-items-start mb-3 flex-wrap gap-2">
                  <div>
                    <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 mb-2">
                      {exp.problemType}
                    </span>
                    <h4 className="fw-bold mb-1">{exp.name}</h4>
                    <p className="text-muted small mb-0">
                      Target Feature: <strong className="text-dark">{exp.targetColumn}</strong> &bull;{' '}
                      Dataset #{exp.datasetId} &bull;{' '}
                      Versions: <strong>{exp.versions?.length || 0}</strong>
                    </p>
                  </div>
                  <div className="d-flex align-items-center gap-2">
                    <span className={`badge ${exp.status === 'COMPLETED' ? 'bg-success' : 'bg-secondary'}`}>
                      {exp.status}
                    </span>
                    <button
                      className="btn btn-sm btn-outline-primary"
                      onClick={() => handleCompare(exp.id)}
                      disabled={comparisonLoading}
                    >
                      <i className="bi bi-columns-gap me-1"></i>Compare versions
                    </button>
                  </div>
                </div>

                {exp.description && (
                  <p className="text-secondary small mb-3">{exp.description}</p>
                )}

                {/* Version History Table */}
                <h6 className="fw-bold mb-2 small text-muted">IMMUTABLE EXPERIMENT VERSIONS</h6>
                <div className="table-responsive">
                  <table className="table table-sm table-bordered align-middle mb-0 small">
                    <thead className="table-light">
                      <tr>
                        <th>Version</th>
                        <th>Best Model Algorithm</th>
                        <th>Validation Score</th>
                        <th>Timestamp</th>
                      </tr>
                    </thead>
                    <tbody>
                      {exp.versions?.map((v) => (
                        <tr key={v.id}>
                          <td><strong>v{v.versionNumber}</strong></td>
                          <td><span className="badge bg-light text-dark border">{v.bestModelName || 'N/A'}</span></td>
                          <td className="fw-semibold text-primary">{v.bestMetricValue?.toFixed(4) || 'N/A'}</td>
                          <td className="text-muted">{new Date(v.createdAt).toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default ExperimentsPage;
