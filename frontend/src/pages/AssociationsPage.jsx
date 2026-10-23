import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  Tooltip,
  Legend,
} from 'chart.js';
import { analysisService } from '../services/analysisService';
import { datasetService } from '../services/datasetService';
import WorkflowNavigator from '../components/WorkflowNavigator';
import { useWorkflow } from '../context/WorkflowContext';

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, Tooltip, Legend);

// Error boundary to prevent any chart or runtime exception from causing an unexplained blank page
class AssociationsErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('Uncaught error in AssociationsPage:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="container py-5 text-center" data-testid="associations-error-boundary">
          <div className="card shadow-sm border-0 p-4 max-w-md mx-auto my-4">
            <i className="bi bi-exclamation-octagon display-4 text-danger mb-3 d-block"></i>
            <h5 className="fw-bold">Associations View Error</h5>
            <p className="text-muted small mb-3">
              An unexpected render issue occurred while rendering the statistical associations.
            </p>
            <div className="d-flex justify-content-center gap-2">
              <button
                className="btn btn-primary btn-sm"
                onClick={() => {
                  this.setState({ hasError: false, error: null });
                  if (this.props.onRetry) this.props.onRetry();
                }}
              >
                <i className="bi bi-arrow-clockwise me-1"></i> Reload View
              </button>
              <Link to="/datasets" className="btn btn-outline-secondary btn-sm">
                <i className="bi bi-database me-1"></i> Datasets
              </Link>
            </div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

const AssociationsPageContent = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { datasetId: workflowDatasetId, selectDataset, refreshWorkflowStatus } = useWorkflow();
  const currentId = id || workflowDatasetId;

  const [dataset, setDataset] = useState(null);
  const [associations, setAssociations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [filterSigOnly, setFilterSigOnly] = useState(false);
  const [filterTestType, setFilterTestType] = useState('ALL');

  const inFlightRef = useRef(false);

  const fetchData = async () => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    try {
      setLoading(true);
      setError('');
      let targetId = currentId;
      if (!targetId) {
        const allDs = await datasetService.listDatasets();
        if (allDs && allDs.data && allDs.data.length > 0) {
          targetId = allDs.data[0].id;
        } else {
          setLoading(false);
          setDataset(null);
          setAssociations([]);
          return;
        }
      }

      if (selectDataset && String(targetId) !== String(workflowDatasetId)) {
        selectDataset(targetId);
      }

      const [dsRes, assocRes] = await Promise.all([
        datasetService.getDataset(targetId),
        analysisService.getAssociations(targetId),
      ]);

      if (dsRes && dsRes.data) setDataset(dsRes.data);
      if (assocRes && assocRes.data) {
        setAssociations(Array.isArray(assocRes.data) ? assocRes.data : []);
      } else {
        setAssociations([]);
      }
    } catch (err) {
      console.error('Failed to load associations:', err);
      setError(
        err.response?.data?.message ||
        err.message ||
        'Failed to load statistical associations.'
      );
      setAssociations([]);
    } finally {
      setLoading(false);
      inFlightRef.current = false;
    }
  };

  useEffect(() => {
    fetchData();
  }, [currentId]);

  const handleRefresh = async () => {
    const targetId = currentId || dataset?.id;
    if (!targetId) return;
    try {
      setRefreshing(true);
      setError('');
      const res = await analysisService.refreshAssociations(targetId);
      if (res && res.data) {
        setAssociations(Array.isArray(res.data) ? res.data : []);
        if (refreshWorkflowStatus) refreshWorkflowStatus();
      }
    } catch (err) {
      console.error('Failed to refresh associations:', err);
      setError(err.response?.data?.message || err.message || 'Failed to refresh associations.');
    } finally {
      setRefreshing(false);
    }
  };

  const handleInvestigate = (assoc) => {
    const targetId = currentId || dataset?.id;
    if (!targetId || !assoc) return;
    navigate(
      `/datasets/${targetId}/research?var1=${encodeURIComponent(assoc.var1)}&var2=${encodeURIComponent(assoc.var2)}&test=${encodeURIComponent(assoc.testType)}`
    );
  };

  const filteredAssociations = useMemo(() => {
    return associations.filter((a) => {
      if (filterSigOnly && !a.isSignificant) return false;
      if (filterTestType !== 'ALL' && a.testType !== filterTestType) return false;
      return true;
    });
  }, [associations, filterSigOnly, filterTestType]);

  const getTestBadge = (type) => {
    switch (type) {
      case 'PEARSON':
        return <span className="badge bg-primary">Pearson Correlation</span>;
      case 'WELCH_T_TEST':
        return <span className="badge bg-success">Welch's t-test</span>;
      case 'ONE_WAY_ANOVA':
        return <span className="badge bg-info text-dark">One-Way ANOVA</span>;
      case 'CHI_SQUARE':
        return <span className="badge bg-warning text-dark">Chi-Square Test</span>;
      default:
        return <span className="badge bg-secondary">{type}</span>;
    }
  };

  const getTestName = (type) => {
    switch (type) {
      case 'PEARSON':
        return 'Pearson Correlation';
      case 'WELCH_T_TEST':
        return "Welch's t-test";
      case 'ONE_WAY_ANOVA':
        return 'One-Way ANOVA';
      case 'CHI_SQUARE':
        return 'Chi-Square Test';
      default:
        return type || 'Unknown';
    }
  };

  // Rows with real, finite confidence intervals
  const ciRows = useMemo(
    () =>
      filteredAssociations.filter(
        (a) =>
          a.ciLower != null &&
          a.ciUpper != null &&
          Number.isFinite(a.ciLower) &&
          Number.isFinite(a.ciUpper)
      ),
    [filteredAssociations]
  );

  const ciTestTypes = useMemo(() => {
    return Array.from(new Set(ciRows.map((r) => r.testType)));
  }, [ciRows]);

  const [ciTestType, setCiTestType] = useState('');

  useEffect(() => {
    if (ciTestTypes.length && !ciTestTypes.includes(ciTestType)) {
      setCiTestType(ciTestTypes[0]);
    }
    if (!ciTestTypes.length && ciTestType) {
      setCiTestType('');
    }
  }, [ciTestTypes, ciTestType]);

  const activeCiRows = useMemo(
    () => ciRows.filter((r) => r.testType === ciTestType),
    [ciRows, ciTestType]
  );

  const ciChartData = useMemo(() => {
    if (!activeCiRows.length) return null;
    const validRows = activeCiRows.filter(
      (r) =>
        r.ciLower != null &&
        r.ciUpper != null &&
        Number.isFinite(r.ciLower) &&
        Number.isFinite(r.ciUpper) &&
        r.statistic != null &&
        Number.isFinite(r.statistic)
    );
    if (!validRows.length) return null;

    const labels = validRows.map((r) => `${r.var1} ↔ ${r.var2}`);
    return {
      labels,
      datasets: [
        {
          type: 'bar',
          label: '95% confidence interval',
          data: validRows.map((r) => [r.ciLower, r.ciUpper]),
          backgroundColor: 'rgba(37, 99, 235, 0.22)',
          borderColor: 'rgba(37, 99, 235, 0.9)',
          borderWidth: 1,
          borderRadius: 3,
          barPercentage: 0.6,
        },
        {
          type: 'scatter',
          label: 'Point estimate',
          data: validRows.map((r) => ({ x: r.statistic, y: `${r.var1} ↔ ${r.var2}` })),
          backgroundColor: 'rgba(239, 68, 68, 0.95)',
          pointRadius: 5,
          pointHoverRadius: 7,
        },
      ],
    };
  }, [activeCiRows]);

  const ciMethodNote = useMemo(() => {
    const sample = activeCiRows[0];
    if (!sample) return '';
    if (sample.ciMethod) return `${sample.ciMethod}${sample.ciTarget ? ` — ${sample.ciTarget}` : ''}.`;
    if (sample.ciUnavailableReason) return sample.ciUnavailableReason;
    return '';
  }, [activeCiRows]);

  const effectiveDatasetId = currentId || dataset?.id;

  return (
    <div className="container py-4">
      <WorkflowNavigator currentStage="associations">
        {/* Breadcrumb */}
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb small">
            <li className="breadcrumb-item"><Link to="/datasets">Datasets</Link></li>
            <li className="breadcrumb-item">
              <Link to={effectiveDatasetId ? `/datasets/${effectiveDatasetId}` : '/datasets'}>
                {dataset?.name || (effectiveDatasetId ? `Dataset #${effectiveDatasetId}` : 'Datasets')}
              </Link>
            </li>
            <li className="breadcrumb-item active" aria-current="page">Associations Matrix</li>
          </ol>
        </nav>

        {/* Header */}
        <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
          <div>
            <h2 className="fw-bold mb-1">
              <i className="bi bi-diagram-3-fill text-success me-2"></i>Statistical Associations Engine
            </h2>
            <p className="text-muted small mb-0">
              Empirical bivariate hypothesis tests: Pearson Correlation, Welch's t-test, ANOVA, and Chi-Square.
            </p>
          </div>
          <button
            className="btn btn-outline-primary d-flex align-items-center gap-2"
            onClick={handleRefresh}
            disabled={refreshing || loading || !effectiveDatasetId}
            data-testid="rerun-associations-btn"
          >
            {refreshing ? (
              <>
                <span className="spinner-border spinner-border-sm" role="status"></span>
                <span>Re-evaluating Pairs...</span>
              </>
            ) : (
              <>
                <i className="bi bi-arrow-clockwise"></i>
                <span>Re-run Association Engine</span>
              </>
            )}
          </button>
        </div>

        {/* Error Alert with Actionable Retry Button */}
        {error && (
          <div
            className="alert alert-danger d-flex align-items-center justify-content-between my-3 p-3 shadow-sm"
            role="alert"
            data-testid="associations-error-alert"
          >
            <div className="d-flex align-items-center gap-2">
              <i className="bi bi-exclamation-triangle-fill fs-5"></i>
              <div>
                <span className="fw-semibold">Error loading associations:</span> {error}
              </div>
            </div>
            <button
              className="btn btn-outline-danger btn-sm d-flex align-items-center gap-1"
              onClick={fetchData}
              data-testid="associations-retry-btn"
            >
              <i className="bi bi-arrow-clockwise"></i>
              <span>Retry</span>
            </button>
          </div>
        )}

        {/* Controls & Filter Bar */}
        <div className="card shadow-sm border-0 mb-4 p-3 bg-white">
          <div className="row g-3 align-items-center">
            <div className="col-md-4">
              <div className="form-check form-switch">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="sigCheck"
                  checked={filterSigOnly}
                  onChange={(e) => setFilterSigOnly(e.target.checked)}
                />
                <label className="form-check-label fw-semibold small" htmlFor="sigCheck">
                  Show Statistically Significant Only (p &lt; 0.05)
                </label>
              </div>
            </div>
            <div className="col-md-5">
              <div className="d-flex align-items-center gap-2">
                <label className="form-label small fw-semibold text-nowrap mb-0" htmlFor="filterTestSelect">
                  Statistical Test:
                </label>
                <select
                  id="filterTestSelect"
                  className="form-select form-select-sm"
                  value={filterTestType}
                  onChange={(e) => setFilterTestType(e.target.value)}
                  aria-label="Filter by statistical test architecture"
                >
                  <option value="ALL">All Test Architectures</option>
                  <option value="PEARSON">Pearson Correlation (Num × Num)</option>
                  <option value="WELCH_T_TEST">Welch's t-test (Num × 2-Cat)</option>
                  <option value="ONE_WAY_ANOVA">One-Way ANOVA (Num × Multi-Cat)</option>
                  <option value="CHI_SQUARE">Chi-Square Independence (Cat × Cat)</option>
                </select>
              </div>
            </div>
            <div className="col-md-3 text-md-end text-muted small">
              Showing <strong>{filteredAssociations.length}</strong> of {associations.length} pairings
            </div>
          </div>
        </div>

        {/* Confidence Interval Explorer */}
        {ciRows.length > 0 && (
          <div className="card shadow-sm border-0 mb-4" data-testid="ci-explorer-card">
            <div className="card-header bg-white py-3 border-0 d-flex flex-wrap justify-content-between align-items-center gap-2">
              <div>
                <h5 className="fw-bold mb-0">Confidence Interval Explorer</h5>
                <small className="text-muted">
                  95% intervals computed with the same estimator as the test. Each architecture is plotted on its own
                  axis because the quantities are not comparable.
                </small>
              </div>
              {ciTestTypes.length > 1 && (
                <select
                  className="form-select form-select-sm w-auto"
                  value={ciTestType}
                  onChange={(e) => setCiTestType(e.target.value)}
                  aria-label="Select the test architecture to plot intervals for"
                >
                  {ciTestTypes.map((t) => (
                    <option key={t} value={t}>
                      {getTestName(t)} ({t})
                    </option>
                  ))}
                </select>
              )}
            </div>
            <div className="card-body">
              {ciChartData ? (
                <>
                  <div style={{ height: `${Math.max(220, activeCiRows.length * 46)}px` }}>
                    <Bar
                      key={`ci-bar-${ciTestType}-${activeCiRows.length}`}
                      data={ciChartData}
                      options={{
                        indexAxis: 'y',
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { position: 'bottom' } },
                        scales: {
                          x: {
                            type: 'linear',
                            title: {
                              display: true,
                              text:
                                ciTestType === 'PEARSON'
                                  ? 'Pearson r'
                                  : 'Mean difference (group 1 − group 2)',
                            },
                          },
                          y: { type: 'category' },
                        },
                      }}
                    />
                  </div>
                  {ciMethodNote && (
                    <p className="small text-muted mb-0 mt-2">
                      <i className="bi bi-info-circle me-1"></i>
                      {ciMethodNote}
                    </p>
                  )}
                </>
              ) : (
                <div className="p-4 text-center bg-light rounded border">
                  <p className="text-muted small mb-0">
                    No confidence interval is available for the selected architecture.
                  </p>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Dynamic Display Logic: Loading vs No Dataset vs Empty Associations vs Table */}
        {loading ? (
          <div className="text-center py-5 my-4" data-testid="associations-loading">
            <div className="spinner-border text-primary" role="status"></div>
            <p className="text-muted small mt-2">Computing empirical pairwise statistics...</p>
          </div>
        ) : !effectiveDatasetId ? (
          <div className="card text-center py-5 border-dashed shadow-sm my-4" data-testid="associations-no-dataset">
            <div className="card-body">
              <i className="bi bi-database-slash display-4 text-warning mb-3 d-block"></i>
              <h5 className="fw-bold">No Dataset Selected</h5>
              <p className="text-muted small max-w-md mx-auto mb-3">
                Statistical association discovery requires an uploaded dataset with at least two variables.
              </p>
              <Link to="/datasets" className="btn btn-primary d-inline-flex align-items-center gap-2">
                <i className="bi bi-cloud-arrow-up-fill"></i>
                <span>Upload or Select Dataset</span>
              </Link>
            </div>
          </div>
        ) : associations.length === 0 ? (
          <div className="card text-center py-5 border-dashed shadow-sm my-4" data-testid="associations-zero-pairs">
            <div className="card-body">
              <i className="bi bi-diagram-3 display-4 text-muted mb-3 d-block"></i>
              <h5 className="fw-bold">No Statistical Associations Discovered</h5>
              <p className="text-muted small max-w-md mx-auto mb-3">
                No eligible pairs of variables met the criteria for bivariate statistical testing (e.g., at least 2 distinct non-identifier columns with sufficient variance and sample size).
              </p>
              <div className="d-flex justify-content-center flex-wrap gap-2">
                <button
                  className="btn btn-outline-primary btn-sm d-flex align-items-center gap-1"
                  onClick={handleRefresh}
                  disabled={refreshing}
                >
                  <i className="bi bi-arrow-clockwise"></i>
                  <span>Re-run Association Engine</span>
                </button>
                <Link to="/datasets" className="btn btn-primary btn-sm d-flex align-items-center gap-1">
                  <i className="bi bi-cloud-arrow-up-fill"></i>
                  <span>Upload Richer Dataset</span>
                </Link>
              </div>
            </div>
          </div>
        ) : filteredAssociations.length === 0 ? (
          <div className="card text-center py-5 border-dashed shadow-sm my-4" data-testid="associations-no-filter-match">
            <div className="card-body">
              <i className="bi bi-funnel display-4 text-muted mb-2 d-block"></i>
              <h6 className="fw-bold">No Associations Found Matching Filters</h6>
              <p className="text-muted small mb-3">
                There are {associations.length} computed bivariate pairing(s), but none match your current filter criteria.
              </p>
              <button
                className="btn btn-outline-secondary btn-sm"
                onClick={() => {
                  setFilterSigOnly(false);
                  setFilterTestType('ALL');
                }}
                data-testid="reset-filters-btn"
              >
                <i className="bi bi-x-circle me-1"></i> Reset Filters
              </button>
            </div>
          </div>
        ) : (
          <div className="card shadow-sm border-0" data-testid="associations-table-card">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light small">
                  <tr>
                    <th className="ps-3">Variable 1</th>
                    <th>Variable 2</th>
                    <th>Test Architecture</th>
                    <th>Statistic</th>
                    <th>p-value</th>
                    <th>Effect Size</th>
                    <th>95% CI</th>
                    <th>Sample Size (N)</th>
                    <th>Significance</th>
                    <th>Interpretation & Scientific Context</th>
                    <th className="text-end pe-3">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAssociations.map((a) => (
                    <tr key={a.id || `${a.var1}-${a.var2}`} className={a.isSignificant ? 'table-light' : ''}>
                      <td className="ps-3 fw-bold text-dark">{a.var1}</td>
                      <td className="fw-bold text-dark">{a.var2}</td>
                      <td>{getTestBadge(a.testType)}</td>
                      <td className="small font-monospace">
                        {a.statistic != null && Number.isFinite(a.statistic) ? a.statistic.toFixed(3) : 'N/A'}
                        {a.degreesOfFreedom != null && (
                          <span className="text-muted ms-1">(df={a.degreesOfFreedom?.toFixed(0)})</span>
                        )}
                      </td>
                      <td>
                        <span className={`badge ${a.pValue < 0.05 ? 'bg-success' : 'bg-secondary'}`}>
                          {a.pValue < 0.0001 ? '< 0.0001' : (a.pValue != null ? a.pValue.toFixed(4) : 'N/A')}
                        </span>
                      </td>
                      <td className="small font-monospace fw-semibold">
                        {a.effectSize != null && Number.isFinite(a.effectSize) ? a.effectSize.toFixed(3) : 'N/A'}
                      </td>
                      <td className="small font-monospace">
                        {a.ciLower != null && a.ciUpper != null && Number.isFinite(a.ciLower) && Number.isFinite(a.ciUpper) ? (
                          <span title={a.ciMethod || a.ciTarget || '95% confidence interval'}>
                            [{a.ciLower.toFixed(3)}, {a.ciUpper.toFixed(3)}]
                          </span>
                        ) : (
                          <span
                            className="text-muted fst-italic"
                            title={a.ciUnavailableReason || 'No confidence interval is defined for this test.'}
                          >
                            n/a
                          </span>
                        )}
                      </td>
                      <td className="small">{a.sampleSize ?? 'N/A'}</td>
                      <td>
                        {a.isSignificant ? (
                          <span className="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25">
                            <i className="bi bi-check-circle-fill me-1"></i>Significant
                          </span>
                        ) : (
                          <span className="badge bg-light text-muted border">
                            Not Significant
                          </span>
                        )}
                      </td>
                      <td className="small text-secondary" style={{ maxWidth: '320px' }}>
                        {a.interpretation || 'No interpretation provided.'}
                      </td>
                      <td className="text-end pe-3">
                        <button
                          className="btn btn-primary btn-sm d-flex align-items-center gap-1 ms-auto"
                          onClick={() => handleInvestigate(a)}
                          title="Formulate Hypothesis & Research Question"
                          data-testid={`investigate-${a.var1}-${a.var2}`}
                        >
                          <i className="bi bi-search"></i>
                          <span>Investigate</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </WorkflowNavigator>
    </div>
  );
};

export default function AssociationsPage() {
  return (
    <AssociationsErrorBoundary>
      <AssociationsPageContent />
    </AssociationsErrorBoundary>
  );
}
