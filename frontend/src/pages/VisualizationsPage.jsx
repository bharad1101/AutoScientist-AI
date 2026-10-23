import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';
import WorkflowNavigator from '../components/WorkflowNavigator';
import EdaGallery from '../components/EdaGallery';
import BoxPlotChart from '../components/BoxPlotChart';
import { useWorkflow } from '../context/WorkflowContext';
import {
  isInAnalysis,
  numericAnalyticColumns,
  categoricalAnalyticColumns,
} from '../utils/columns';
import { Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  BarController,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';

ChartJS.register(BarController, CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

class VisualizationsErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('VisualizationsPage render failure:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="container py-5" data-testid="visualizations-error-boundary">
          <div className="card shadow-sm border-0 border-danger">
            <div className="card-header bg-danger text-white py-3">
              <h5 className="mb-0 fw-bold">
                <i className="bi bi-exclamation-triangle-fill me-2"></i>
                Visualization Render Failure
              </h5>
            </div>
            <div className="card-body p-4 text-center">
              <p className="lead text-dark mb-2">
                An unexpected error occurred while rendering the visualization gallery.
              </p>
              <p className="text-muted small mb-4 font-monospace bg-light p-3 rounded text-start">
                {this.state.error?.message || String(this.state.error)}
              </p>
              <div className="d-flex justify-content-center gap-3">
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => {
                    this.setState({ hasError: false, error: null });
                    window.location.reload();
                  }}
                >
                  <i className="bi bi-arrow-clockwise me-1"></i> Reload Visualizations
                </button>
                <Link to="/datasets" className="btn btn-outline-secondary">
                  <i className="bi bi-database me-1"></i> Back to Datasets
                </Link>
              </div>
            </div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

function VisualizationsPageContent() {
  const { id } = useParams();
  const { datasetId: workflowDatasetId, selectDataset } = useWorkflow();
  const activeId = id || workflowDatasetId;

  const [dataset, setDataset] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [corrMatrix, setCorrMatrix] = useState(null);
  const [boxPlots, setBoxPlots] = useState([]);
  const [selectedNumVar, setSelectedNumVar] = useState('');
  const [selectedCatVar, setSelectedCatVar] = useState('');
  const [groupedSummary, setGroupedSummary] = useState([]);
  const [loadingGrouped, setLoadingGrouped] = useState(false);

  const requestSeq = useRef(0);

  const loadData = useCallback(async (currentId) => {
    if (!currentId) {
      setLoading(false);
      return;
    }
    const seq = ++requestSeq.current;
    try {
      setLoading(true);
      setError('');
      if (selectDataset) selectDataset(currentId);

      const [dsRes, corrRes, boxRes] = await Promise.all([
        datasetService.getDataset(currentId),
        analysisService.getCorrelationMatrix(currentId).catch(() => null),
        analysisService.getBoxPlots(currentId).catch(() => null),
      ]);

      if (seq !== requestSeq.current) return;

      if (dsRes && dsRes.data) {
        setDataset(dsRes.data);
        const cols = dsRes.data.columns || [];

        // Filter and rank meaningful numeric variables, excluding identifiers
        const numVars = cols
          .filter(isInAnalysis)
          .filter((c) => c.dataType === 'NUMERIC')
          .sort((a, b) => {
            const highValueTerms = ['age', 'salary', 'income', 'score', 'hours', 'amount', 'bp', 'rate', 'balance'];
            const aHas = highValueTerms.some((t) => a.columnName.toLowerCase().includes(t));
            const bHas = highValueTerms.some((t) => b.columnName.toLowerCase().includes(t));
            if (aHas && !bHas) return -1;
            if (!aHas && bHas) return 1;
            return 0;
          });

        const catVars = cols.filter(
          (c) =>
            (c.dataType === 'CATEGORICAL' || c.dataType === 'BOOLEAN') && isInAnalysis(c)
        );

        const initialNum = numVars.length > 0 ? numVars[0].columnName : '';
        const initialCat = catVars.length > 0 ? catVars[0].columnName : '';

        setSelectedNumVar(initialNum);
        setSelectedCatVar(initialCat);

        if (initialNum && initialCat) {
          const grp = await analysisService
            .getGroupedSummary(currentId, initialNum, initialCat)
            .catch(() => null);
          if (seq === requestSeq.current && grp && grp.data) {
            setGroupedSummary(grp.data);
          }
        }
      }

      if (seq === requestSeq.current) {
        if (corrRes && corrRes.data) setCorrMatrix(corrRes.data);
        if (boxRes && boxRes.data) setBoxPlots(boxRes.data);
      }
    } catch (err) {
      if (seq === requestSeq.current) {
        console.error('Failed to load visualization data:', err);
        setError(err.response?.data?.message || err.message || 'Failed to load visualization data.');
      }
    } finally {
      if (seq === requestSeq.current) {
        setLoading(false);
      }
    }
  }, [selectDataset]);

  useEffect(() => {
    if (activeId) {
      loadData(activeId);
    } else {
      datasetService
        .listDatasets()
        .then((res) => {
          if (res.data && res.data.length > 0) {
            loadData(res.data[0].id);
          } else {
            setLoading(false);
          }
        })
        .catch(() => setLoading(false));
    }
  }, [activeId, loadData]);

  const handleGroupedChange = async (num, cat) => {
    setSelectedNumVar(num);
    setSelectedCatVar(cat);
    if (!num || !cat || !activeId) return;
    try {
      setLoadingGrouped(true);
      const res = await analysisService.getGroupedSummary(activeId, num, cat);
      if (res && res.data) {
        setGroupedSummary(res.data);
      } else {
        setGroupedSummary([]);
      }
    } catch (err) {
      console.error('Failed to update grouped summary:', err);
      setGroupedSummary([]);
    } finally {
      setLoadingGrouped(false);
    }
  };

  const boxPlotChartData = useMemo(
    () =>
      (boxPlots || [])
        .map((bp) => ({
          label: bp.variable || bp.variableName,
          min: bp.min,
          q1: bp.q1,
          median: bp.median,
          q3: bp.q3,
          max: bp.max,
          iqr: bp.iqr,
          outliers: bp.outliers || [],
        }))
        .filter(
          (d) =>
            d.label &&
            [d.min, d.q1, d.median, d.q3, d.max].every((v) => typeof v === 'number' && Number.isFinite(v))
        ),
    [boxPlots]
  );

  // Significance lookup for the correlation heat-map: key = "row|col".
  const correlationSignificance = useMemo(() => {
    const map = new Map();
    const vars = corrMatrix?.variables || [];
    const p = corrMatrix?.pValues;
    if (!p || !Array.isArray(p)) return map;
    vars.forEach((rowVar, r) => {
      (p[r] || []).forEach((pv, c) => {
        map.set(`${rowVar}|${vars[c]}`, pv);
      });
    });
    return map;
  }, [corrMatrix]);

  const significantPairs = useMemo(() => {
    let count = 0;
    const vars = corrMatrix?.variables || [];
    const p = corrMatrix?.pValues;
    if (!p || !Array.isArray(p)) return 0;
    for (let r = 0; r < vars.length; r++) {
      for (let c = r + 1; c < vars.length; c++) {
        const pv = p[r]?.[c];
        if (pv != null && pv < 0.05) count++;
      }
    }
    return count;
  }, [corrMatrix]);

  const columns = dataset?.columns || [];
  const numCols = numericAnalyticColumns(columns);
  const catCols = categoricalAnalyticColumns(columns);

  // Grouped chart data
  const hasValidObservations =
    groupedSummary &&
    groupedSummary.length > 0 &&
    groupedSummary.some((g) => g.count > 0 && g.mean != null && !isNaN(g.mean));

  const groupedChartData = hasValidObservations
    ? {
        labels: groupedSummary.map((g) => g.groupValue || g.groupName),
        datasets: [
          {
            label: `Mean of ${selectedNumVar}`,
            data: groupedSummary.map((g) => g.mean),
            backgroundColor: 'rgba(54, 162, 235, 0.75)',
            borderColor: 'rgba(54, 162, 235, 1)',
            borderWidth: 1,
            borderRadius: 4,
          },
          {
            label: `Median of ${selectedNumVar}`,
            data: groupedSummary.map((g) => g.median),
            backgroundColor: 'rgba(255, 159, 64, 0.75)',
            borderColor: 'rgba(255, 159, 64, 1)',
            borderWidth: 1,
            borderRadius: 4,
          },
        ],
      }
    : null;

  if (loading) {
    return (
      <div className="container py-5 text-center" data-testid="visualizations-loading">
        <div className="spinner-border text-primary" role="status"></div>
        <p className="text-muted mt-2 small">Rendering scientific visualizations...</p>
      </div>
    );
  }

  if (!dataset && !activeId) {
    return (
      <div className="container py-5 text-center" data-testid="visualizations-no-dataset">
        <div className="card shadow-sm border-0 p-5 mx-auto" style={{ maxWidth: '600px' }}>
          <div className="mb-3">
            <i className="bi bi-pie-chart text-muted" style={{ fontSize: '3rem' }}></i>
          </div>
          <h4 className="fw-bold mb-2">No Dataset Active for Visualizations</h4>
          <p className="text-muted mb-4">
            Upload or select a dataset to unlock bivariate scatter plots, correlation heatmaps, empirical box plots, and cohort comparisons.
          </p>
          <div className="d-flex justify-content-center gap-3">
            <Link to="/datasets" className="btn btn-primary d-inline-flex align-items-center gap-2">
              <i className="bi bi-cloud-arrow-up-fill"></i>
              <span>Upload Dataset</span>
            </Link>
            <Link to="/dashboard" className="btn btn-outline-secondary">
              Go to Dashboard
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-4">
      <WorkflowNavigator currentStage="visualizations">
        <div className="mb-4 d-flex justify-content-between align-items-center flex-wrap gap-2">
          <div>
            <h3 className="fw-bold mb-1">
              <i className="bi bi-pie-chart-fill text-primary me-2"></i>
              Scientific Visualizations & Distribution Gallery
            </h3>
            <p className="text-secondary small mb-0">
              Pairwise correlation structures, demographic group comparisons, and box plot distributions.
            </p>
          </div>
          {dataset && (
            <span className="badge bg-light text-dark border p-2">
              <i className="bi bi-database me-1 text-primary"></i>
              <strong>{dataset.name}</strong> ({dataset.rowCount} rows)
            </span>
          )}
        </div>

        {error && (
          <div className="alert alert-danger mb-4 shadow-sm d-flex justify-content-between align-items-center" role="alert">
            <div>
              <i className="bi bi-exclamation-triangle-fill me-2"></i>
              {error}
            </div>
            <button
              className="btn btn-sm btn-outline-danger"
              onClick={() => loadData(activeId)}
            >
              <i className="bi bi-arrow-clockwise me-1"></i> Retry
            </button>
          </div>
        )}

        <div className="mb-2">
          <h5 className="fw-bold mb-1">
            <i className="bi bi-grid-1x2-fill text-primary me-2"></i>
            Advanced EDA Gallery
          </h5>
          <p className="text-secondary small mb-3">
            Histograms, row-level scatter, missingness, categorical composition and time-series trends — all computed
            from the canonical stored dataset, never synthetic values.
          </p>
        </div>
        <EdaGallery datasetId={activeId} columns={columns} />

        {/* 1. Grouped Feature Breakdown Chart */}
        <div className="card shadow-sm border-0 mb-4">
          <div className="card-header bg-white py-3 border-bottom d-flex flex-wrap justify-content-between align-items-center gap-3">
            <div>
              <h5 className="fw-bold mb-0">
                <i className="bi bi-bar-chart-steps text-primary me-2"></i>
                Bivariate Distribution & Group Breakdown
              </h5>
              <small className="text-muted">Compare continuous variable metrics partitioned across categorical cohorts.</small>
            </div>
            <div className="d-flex flex-wrap gap-2">
              <select
                className="form-select form-select-sm"
                value={selectedNumVar}
                onChange={(e) => handleGroupedChange(e.target.value, selectedCatVar)}
                style={{ width: 'auto' }}
                aria-label="Select continuous variable for group comparison"
              >
                {numCols.length === 0 ? (
                  <option value="">No continuous variables</option>
                ) : (
                  numCols.map((c) => (
                    <option key={c.columnName} value={c.columnName}>
                      Numeric: {c.columnName}
                    </option>
                  ))
                )}
              </select>
              <select
                className="form-select form-select-sm"
                value={selectedCatVar}
                onChange={(e) => handleGroupedChange(selectedNumVar, e.target.value)}
                style={{ width: 'auto' }}
                aria-label="Select grouping categorical variable"
              >
                {catCols.length === 0 ? (
                  <option value="">No categorical variables</option>
                ) : (
                  catCols.map((c) => (
                    <option key={c.columnName} value={c.columnName}>
                      Group: {c.columnName}
                    </option>
                  ))
                )}
              </select>
            </div>
          </div>
          <div className="card-body p-4">
            {loadingGrouped ? (
              <div className="text-center py-4">
                <div className="spinner-border spinner-border-sm text-primary mb-2"></div>
                <div className="small text-muted">Computing cohort distributions...</div>
              </div>
            ) : groupedChartData ? (
              <div style={{ height: '340px' }}>
                <Bar
                  data={groupedChartData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: { position: 'top' },
                    },
                    scales: {
                      y: {
                        title: { display: true, text: selectedNumVar },
                      },
                      x: {
                        title: { display: true, text: selectedCatVar },
                      },
                    },
                  }}
                />
              </div>
            ) : (
              <div className="p-5 text-center bg-light rounded border">
                <i className="bi bi-bar-chart text-muted fs-1 d-block mb-2"></i>
                <h6 className="fw-semibold text-secondary mb-1">No valid observations available for this visualization.</h6>
                <p className="text-muted small mb-0">
                  Select alternative continuous and grouping features above to render empirical distribution comparisons.
                </p>
              </div>
            )}
          </div>
        </div>

        {/* 2. Correlation Matrix Heatmap */}
        {corrMatrix && Array.isArray(corrMatrix.variables) && corrMatrix.variables.length > 0 && Array.isArray(corrMatrix.matrix) && (
          <div className="card shadow-sm border-0 mb-4">
            <div className="card-header bg-white py-3 border-bottom">
              <h5 className="fw-bold mb-0">
                <i className="bi bi-grid-3x3-gap-fill text-success me-2"></i>
                Pearson Correlation Structure
              </h5>
              <small className="text-muted">
                Empirical pairwise correlation coefficients (-1.0 to +1.0) between numeric features (Identifiers
                excluded). Cells marked with * are statistically significant at p &lt; 0.05.
                {significantPairs > 0 && ` ${significantPairs} significant pair(s) detected.`}
              </small>
            </div>
            <div className="card-body p-3">
              <div className="table-responsive">
                <table className="table table-sm table-bordered text-center align-middle mb-0 font-monospace small">
                  <thead className="table-light">
                    <tr>
                      <th className="text-start">Feature</th>
                      {corrMatrix.variables.map((v) => (
                        <th key={v} className="text-truncate" style={{ maxWidth: '110px' }}>
                          {v}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {corrMatrix.variables.map((rowVar, rIdx) => {
                      const row = corrMatrix.matrix[rIdx];
                      if (!Array.isArray(row)) return null;
                      return (
                        <tr key={rowVar}>
                          <td className="text-start fw-bold text-dark">{rowVar}</td>
                          {row.map((val, cIdx) => {
                            if (val == null || !Number.isFinite(val)) {
                              return <td key={cIdx} className="text-muted">N/A</td>;
                            }
                            let bg = '#ffffff';
                            let text = '#000000';
                            if (rIdx === cIdx) {
                              bg = '#e9ecef';
                            } else if (val > 0.5) {
                              bg = '#d1e7dd';
                              text = '#0f5132';
                            } else if (val > 0.2) {
                              bg = '#e8f5e9';
                            } else if (val < -0.5) {
                              bg = '#f8d7da';
                              text = '#842029';
                            } else if (val < -0.2) {
                              bg = '#fce4ec';
                            }
                            const pValue = correlationSignificance.get(`${rowVar}|${corrMatrix.variables[cIdx]}`);
                            const isSignificant = rIdx !== cIdx && pValue != null && pValue < 0.05;
                            return (
                              <td
                                key={cIdx}
                                style={{
                                  backgroundColor: bg,
                                  color: text,
                                  padding: '8px',
                                  fontWeight: isSignificant ? 700 : 400,
                                }}
                                title={
                                  rIdx === cIdx
                                    ? 'Self-correlation (r = 1.0)'
                                    : `r = ${val.toFixed(4)}${
                                        pValue != null
                                          ? ` | p = ${pValue < 0.0001 ? '< 0.0001' : pValue.toFixed(4)}`
                                          : ' | p-value unavailable'
                                      }${
                                        corrMatrix.sampleSizes?.length
                                          ? ` | n = ${corrMatrix.sampleSizes[Math.max(rIdx, cIdx)]}`
                                          : ''
                                      }`
                                }
                              >
                                {val.toFixed(2)}
                                {isSignificant && <span className="ms-1">*</span>}
                              </td>
                            );
                          })}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* 3. Five-Number Distribution Summary (Box Plots) */}
        {boxPlots && Array.isArray(boxPlots) && boxPlots.length > 0 && (
          <div className="card shadow-sm border-0 mb-4">
            <div className="card-header bg-white py-3 border-bottom">
              <h5 className="fw-bold mb-0">
                <i className="bi bi-symmetry-vertical text-info me-2"></i>
                Five-Number Distribution Summary
              </h5>
              <small className="text-muted">
                Min, Q1, Median, Q3, Max, IQR, and Outlier frequency for continuous variables.
              </small>
            </div>
            {boxPlotChartData.length > 0 && (
              <div className="card-body p-4 border-bottom">
                <h6 className="fw-bold small text-uppercase text-muted mb-2">
                  <i className="bi bi-bar-chart-steps me-1"></i>Box &amp; Whisker Plot
                </h6>
                <BoxPlotChart data={boxPlotChartData} height={Math.max(320, boxPlotChartData.length * 40)} />
                <p className="small text-muted mb-0 mt-2">
                  <i className="bi bi-info-circle me-1"></i>
                  Boxes span Q1–Q3, the dark line is the median, whiskers reach the observed min/max and red points are
                  outliers (1.5 × IQR rule) detected on the canonical dataset.
                </p>
              </div>
            )}
            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0">
                  <thead className="table-light">
                    <tr>
                      <th>Variable</th>
                      <th>Min</th>
                      <th>Q1 (25%)</th>
                      <th>Median (50%)</th>
                      <th>Q3 (75%)</th>
                      <th>Max</th>
                      <th>IQR</th>
                      <th>Outliers Detected</th>
                    </tr>
                  </thead>
                  <tbody>
                    {boxPlots.map((bp) => {
                      const vName = bp.variable || bp.variableName;
                      return (
                        <tr key={vName}>
                          <td className="fw-bold text-primary">{vName}</td>
                          <td>{typeof bp.min === 'number' && Number.isFinite(bp.min) ? bp.min.toFixed(2) : 'N/A'}</td>
                          <td>{typeof bp.q1 === 'number' && Number.isFinite(bp.q1) ? bp.q1.toFixed(2) : 'N/A'}</td>
                          <td className="fw-bold text-dark">{typeof bp.median === 'number' && Number.isFinite(bp.median) ? bp.median.toFixed(2) : 'N/A'}</td>
                          <td>{typeof bp.q3 === 'number' && Number.isFinite(bp.q3) ? bp.q3.toFixed(2) : 'N/A'}</td>
                          <td>{typeof bp.max === 'number' && Number.isFinite(bp.max) ? bp.max.toFixed(2) : 'N/A'}</td>
                          <td>{typeof bp.iqr === 'number' && Number.isFinite(bp.iqr) ? bp.iqr.toFixed(2) : 'N/A'}</td>
                          <td>
                            {bp.outliers && bp.outliers.length > 0 ? (
                              <span className="badge bg-warning text-dark">
                                {bp.outliers.length} outliers ({bp.outliers.slice(0, 3).map((o) => typeof o === 'number' ? o.toFixed(1) : o).join(', ')}...)
                              </span>
                            ) : (
                              <span className="badge bg-success-subtle text-success">None detected</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </WorkflowNavigator>
    </div>
  );
}

export default function VisualizationsPage() {
  return (
    <VisualizationsErrorBoundary>
      <VisualizationsPageContent />
    </VisualizationsErrorBoundary>
  );
}
