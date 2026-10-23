import React, { useState, useEffect } from 'react';
import { useParams, Link, useLocation, useNavigate } from 'react-router-dom';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';
import WorkflowNavigator from '../components/WorkflowNavigator';
import {
  numericAnalyticColumns,
  categoricalAnalyticColumns,
  isInAnalysis,
} from '../utils/columns';

const DatasetDetailPage = () => {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [dataset, setDataset] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Derive active tab and current stage from route path
  let currentStage = 'overview';
  if (location.pathname.includes('/quality')) {
    currentStage = 'quality';
  } else if (location.pathname.includes('/eda')) {
    currentStage = 'eda';
  }
  const [activeTab, setActiveTab] = useState(currentStage);

  useEffect(() => {
    setActiveTab(currentStage);
    if (currentStage === 'eda') {
      loadEda();
    }
  }, [currentStage]);

  // EDA State
  const [corrMatrix, setCorrMatrix] = useState(null);
  const [boxPlots, setBoxPlots] = useState([]);
  const [groupedSummary, setGroupedSummary] = useState([]);
  const [selectedNumVar, setSelectedNumVar] = useState('');
  const [selectedCatVar, setSelectedCatVar] = useState('');
  const [loadingEda, setLoadingEda] = useState(false);

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        setLoading(true);
        setError('');
        const res = await datasetService.getDataset(id);
        if (res && res.data) {
          setDataset(res.data);
        }
      } catch (err) {
        setError(err.response?.data?.message || 'Failed to load dataset details.');
      } finally {
        setLoading(false);
      }
    };

    fetchDetail();
  }, [id]);

  const loadEda = async () => {
    if (corrMatrix) return;
    try {
      setLoadingEda(true);
      const [corrRes, boxRes] = await Promise.all([
        analysisService.getCorrelationMatrix(id),
        analysisService.getBoxPlots(id),
      ]);
      if (corrRes && corrRes.data) setCorrMatrix(corrRes.data);
      if (boxRes && boxRes.data) setBoxPlots(boxRes.data);

      if (dataset && dataset.columns) {
        const numVars = numericAnalyticColumns(dataset.columns);
        const catVars = categoricalAnalyticColumns(dataset.columns);
        if (numVars.length > 0 && catVars.length > 0) {
          const defaultNum = numVars[0].columnName;
          const defaultCat = catVars[0].columnName;
          setSelectedNumVar(defaultNum);
          setSelectedCatVar(defaultCat);
          const grpRes = await analysisService.getGroupedSummary(id, defaultNum, defaultCat);
          if (grpRes && grpRes.data) setGroupedSummary(grpRes.data);
        }
      }
    } catch (err) {
      console.error('Failed to load EDA data:', err);
    } finally {
      setLoadingEda(false);
    }
  };

  const handleGroupedChange = async (num, cat) => {
    setSelectedNumVar(num);
    setSelectedCatVar(cat);
    if (!num || !cat) return;
    try {
      const res = await analysisService.getGroupedSummary(id, num, cat);
      if (res && res.data) setGroupedSummary(res.data);
    } catch (err) {
      console.error('Failed to load grouped summary:', err);
    }
  };

  if (loading) {
    return (
      <div className="container py-5 text-center">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading dataset profile...</span>
        </div>
        <p className="text-muted mt-2 small">Analyzing metadata and statistical profiles...</p>
      </div>
    );
  }

  if (error || !dataset) {
    return (
      <div className="container py-5">
        <div className="alert alert-danger" role="alert">
          <i className="bi bi-exclamation-octagon-fill me-2"></i>
          {error || 'Dataset not found.'}
        </div>
        <Link to="/datasets" className="btn btn-outline-secondary">
          <i className="bi bi-arrow-left me-1"></i> Back to Datasets
        </Link>
      </div>
    );
  }

  const quality = dataset.qualityReport || {};
  const issues = quality.issues || [];
  const columns = dataset.columns || [];

  const getSeverityBadge = (severity) => {
    switch (severity) {
      case 'ERROR':
        return <span className="badge bg-danger"><i className="bi bi-x-circle me-1"></i>ERROR</span>;
      case 'WARNING':
        return <span className="badge bg-warning text-dark"><i className="bi bi-exclamation-triangle me-1"></i>WARNING</span>;
      default:
        return <span className="badge bg-info text-dark"><i className="bi bi-info-circle me-1"></i>INFO</span>;
    }
  };

  return (
    <div className="container py-4">
      <WorkflowNavigator currentStage={currentStage}>
        {/* Breadcrumb & Navigation */}
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb small">
            <li className="breadcrumb-item"><Link to="/datasets">Datasets</Link></li>
            <li className="breadcrumb-item active" aria-current="page">{dataset.name}</li>
          </ol>
        </nav>

        {/* Dataset Header */}
        <div className="card shadow-sm border-0 mb-4 p-4 bg-white">
          <div className="d-flex justify-content-between align-items-start flex-wrap gap-3">
            <div>
              <div className="d-flex align-items-center gap-2 mb-1">
                <h2 className="fw-bold mb-0">{dataset.name}</h2>
                <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 px-2 py-1">
                  {dataset.datasetType || 'MIXED'}
                </span>
              </div>
              <p className="text-muted small mb-2">
                <i className="bi bi-file-earmark-code me-1"></i> {dataset.originalFilename} &bull;{' '}
                <i className="bi bi-calendar3 me-1"></i> Ingested on {new Date(dataset.createdAt).toLocaleString()}
              </p>
              {dataset.description && (
                <p className="text-secondary small mb-0">{dataset.description}</p>
              )}
            </div>

            <div className="d-flex flex-wrap gap-2">
              <Link to={`/datasets/${dataset.id}/associations`} className="btn btn-success d-flex align-items-center gap-1">
                <i className="bi bi-diagram-3-fill"></i>
                <span>Find Associations</span>
              </Link>
              <Link to={`/datasets/${dataset.id}/research`} className="btn btn-outline-primary d-flex align-items-center gap-1">
                <i className="bi bi-question-circle-fill"></i>
                <span>Research Questions</span>
              </Link>
            </div>
          </div>

          {/* Quick Summary Metrics */}
          <div className="row g-3 mt-3 pt-3 border-top">
            <div className="col-6 col-md-3">
              <div className="p-2 border rounded bg-light">
                <span className="text-muted small d-block">TOTAL OBSERVATIONS</span>
                <span className="fs-5 fw-bold text-dark">{dataset.rowCount?.toLocaleString()}</span>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="p-2 border rounded bg-light">
                <span className="text-muted small d-block">FEATURE DIMENSIONS</span>
                <span className="fs-5 fw-bold text-dark">{dataset.columnCount} variables</span>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="p-2 border rounded bg-light">
                <span className="text-muted small d-block">FILE STORAGE SIZE</span>
                <span className="fs-5 fw-bold text-dark">{(dataset.fileSize / 1024).toFixed(1)} KB</span>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="p-2 border rounded bg-light">
                <span className="text-muted small d-block">QUALITY AUDIT SCORE</span>
                <span className={`fs-5 fw-bold ${quality.overallScore >= 80 ? 'text-success' : quality.overallScore >= 50 ? 'text-warning' : 'text-danger'}`}>
                  {quality.overallScore?.toFixed(1) || '0.0'} / 100
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Tabs */}
        <ul className="nav nav-pills mb-4 bg-white p-2 rounded shadow-sm border">
          <li className="nav-item">
            <button
              className={`nav-link ${activeTab === 'overview' ? 'active' : ''}`}
              onClick={() => navigate(`/datasets/${dataset.id}/overview`)}
            >
              <i className="bi bi-bar-chart-steps me-1"></i> Profiling & Columns ({columns.length})
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`nav-link ${activeTab === 'eda' ? 'active' : ''}`}
              onClick={() => {
                navigate(`/datasets/${dataset.id}/eda`);
                loadEda();
              }}
            >
              <i className="bi bi-graph-up me-1"></i> Exploratory Data Analysis (EDA)
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`nav-link ${activeTab === 'quality' ? 'active' : ''}`}
              onClick={() => navigate(`/datasets/${dataset.id}/quality`)}
            >
              <i className="bi bi-shield-check me-1"></i> Data Quality Audit ({issues.length} Issues)
            </button>
          </li>
        </ul>

      {/* Tab 1: Column Profiling */}
      {activeTab === 'overview' && (
        <div className="card shadow-sm border-0">
          <div className="card-header bg-white py-3">
            <h5 className="fw-bold mb-0">Feature Columns Profile</h5>
            <span className="text-muted small">Inferred data types, parametric statistics, and missingness metrics.</span>
          </div>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light small">
                <tr>
                  <th className="ps-3">#</th>
                  <th>Variable Name</th>
                  <th>Inferred Type</th>
                  <th>Missingness</th>
                  <th>Cardinality</th>
                  <th>Parametric Statistics (Min / Mean / Max / StdDev)</th>
                  <th>Distribution Profile</th>
                </tr>
              </thead>
              <tbody>
                {columns.map((col, idx) => {
                  let categories = {};
                  try {
                    if (col.categoriesJson) categories = JSON.parse(col.categoriesJson);
                  } catch (e) {}

                  return (
                    <tr key={col.id || idx}>
                      <td className="ps-3 text-muted small">{idx + 1}</td>
                      <td>
                        <span className="fw-bold text-dark">{col.columnName}</span>
                      </td>
                      <td>
                        <span className={`badge ${
                          col.dataType === 'NUMERIC' ? 'bg-primary' :
                          col.dataType === 'CATEGORICAL' ? 'bg-success' :
                          col.dataType === 'BOOLEAN' ? 'bg-warning text-dark' :
                          col.dataType === 'DATE_TIME' ? 'bg-info text-dark' : 'bg-secondary'
                        }`}>
                          {col.dataType}
                        </span>
                        <span className="text-muted ms-1 small" title="Type inference confidence">
                          ({Math.round((col.typeConfidence || 1.0) * 100)}%)
                        </span>
                        {!isInAnalysis(col) && (
                          <span
                            className="badge bg-light text-dark border ms-1"
                            title={
                              col.excluded
                                ? 'Excluded from analysis by you'
                                : 'Identifier column — never used implicitly in statistics, charts or models'
                            }
                          >
                            <i className="bi bi-slash-circle me-1"></i>
                            not in analysis
                          </span>
                        )}
                      </td>
                      <td>
                        <div className="d-flex align-items-center gap-2">
                          <div className="progress flex-grow-1" style={{ width: '60px', height: '6px' }}>
                            <div
                              className={`progress-bar ${col.missingPct > 20 ? 'bg-danger' : col.missingPct > 0 ? 'bg-warning' : 'bg-success'}`}
                              role="progressbar"
                              style={{ width: `${Math.min(100, col.missingPct || 0)}%` }}
                            ></div>
                          </div>
                          <span className="small text-muted">{col.missingCount} ({col.missingPct}%)</span>
                        </div>
                      </td>
                      <td>
                        <span className="badge bg-light text-dark border">
                          {col.uniqueCount} distinct
                        </span>
                      </td>
                      <td className="small">
                        {col.dataType === 'NUMERIC' ? (
                          <div>
                            <div><strong>Min:</strong> {col.minVal?.toFixed(2)} &bull; <strong>Max:</strong> {col.maxVal?.toFixed(2)}</div>
                            <div className="text-muted">
                              <strong>Mean:</strong> {col.meanVal?.toFixed(2)} &plusmn; {col.stdDev?.toFixed(2)}
                            </div>
                            <div className="text-muted">
                              <strong>Median:</strong> {col.medianVal?.toFixed(2)} (Q1: {col.q1?.toFixed(2)}, Q3: {col.q3?.toFixed(2)})
                            </div>
                          </div>
                        ) : (
                          <span className="text-muted">N/A (Categorical)</span>
                        )}
                      </td>
                      <td>
                        {col.dataType === 'NUMERIC' ? (
                          <div className="small text-secondary">
                            <span className="badge bg-light text-muted border">
                              Skewness: {col.skewness?.toFixed(2)}
                            </span>
                          </div>
                        ) : (
                          <div className="d-flex flex-wrap gap-1" style={{ maxWidth: '280px' }}>
                            {Object.entries(categories).slice(0, 4).map(([cat, cnt]) => (
                              <span key={cat} className="badge bg-light text-secondary border small">
                                {cat}: {cnt}
                              </span>
                            ))}
                            {Object.keys(categories).length > 4 && (
                              <span className="badge bg-light text-muted small">
                                +{Object.keys(categories).length - 4} more
                              </span>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 2: Exploratory Data Analysis (EDA) */}
      {activeTab === 'eda' && (
        <div>
          {loadingEda ? (
            <div className="card shadow-sm border-0 p-5 text-center bg-white">
              <div className="spinner-border text-primary mx-auto mb-3" role="status"></div>
              <h6 className="fw-bold">Computing Empirical Exploratory Data Analysis...</h6>
              <p className="text-muted small mb-0">Calculating covariance matrices, box plot quartiles, and cross-tabulations.</p>
            </div>
          ) : (
            <>
              {/* Correlation Matrix */}
              <div className="card shadow-sm border-0 mb-4 bg-white">
                <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center">
                  <div>
                    <h5 className="fw-bold mb-0">
                      <i className="bi bi-grid-3x3-gap-fill text-primary me-2"></i>Pearson Correlation Matrix
                    </h5>
                    <span className="text-muted small">Pairwise continuous linear correlations with aligned paired observations.</span>
                  </div>
                </div>
                <div className="card-body">
                  {!corrMatrix || !corrMatrix.variables || corrMatrix.variables.length === 0 ? (
                    <p className="text-muted small mb-0">No continuous numeric variables available for correlation analysis.</p>
                  ) : (
                    <div className="table-responsive">
                      <table className="table table-bordered text-center align-middle mb-0 small">
                        <thead className="table-light">
                          <tr>
                            <th className="text-start">Feature</th>
                            {corrMatrix.variables.map((v) => (
                              <th key={v} className="text-truncate" style={{ maxWidth: '120px' }}>{v}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {corrMatrix.variables.map((rowVar, rIdx) => (
                            <tr key={rowVar}>
                              <th className="table-light text-start text-truncate" style={{ maxWidth: '140px' }}>{rowVar}</th>
                              {corrMatrix.matrix[rIdx].map((val, cIdx) => {
                                const absVal = Math.abs(val);
                                let bg = '#ffffff';
                                let textColor = '#000000';
                                if (rIdx === cIdx) {
                                  bg = '#e2e8f0';
                                } else if (val > 0) {
                                  bg = `rgba(13, 110, 253, ${Math.min(0.85, Math.max(0.08, absVal))})`;
                                  if (absVal > 0.5) textColor = '#ffffff';
                                } else if (val < 0) {
                                  bg = `rgba(220, 53, 69, ${Math.min(0.85, Math.max(0.08, absVal))})`;
                                  if (absVal > 0.5) textColor = '#ffffff';
                                }

                                return (
                                  <td
                                    key={cIdx}
                                    style={{ backgroundColor: bg, color: textColor, fontWeight: absVal > 0.4 ? '600' : 'normal' }}
                                    title={`r(${rowVar}, ${corrMatrix.variables[cIdx]}) = ${val.toFixed(3)}`}
                                  >
                                    {val.toFixed(2)}
                                  </td>
                                );
                              })}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>

              {/* Box Plot & Distribution Spread */}
              <div className="card shadow-sm border-0 mb-4 bg-white">
                <div className="card-header bg-white py-3">
                  <h5 className="fw-bold mb-0">
                    <i className="bi bi-box text-success me-2"></i>Continuous Distribution Spreads (Box Plot Metrics)
                  </h5>
                  <span className="text-muted small">Five-number summary, Interquartile Range (IQR), and empirical outliers.</span>
                </div>
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0 small">
                    <thead className="table-light">
                      <tr>
                        <th className="ps-3">Feature</th>
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
                      {boxPlots.map((bp) => (
                        <tr key={bp.variable}>
                          <td className="ps-3 fw-bold text-dark">{bp.variable}</td>
                          <td>{bp.min?.toFixed(2)}</td>
                          <td>{bp.q1?.toFixed(2)}</td>
                          <td className="fw-bold text-primary">{bp.median?.toFixed(2)}</td>
                          <td>{bp.q3?.toFixed(2)}</td>
                          <td>{bp.max?.toFixed(2)}</td>
                          <td>{bp.iqr?.toFixed(2)}</td>
                          <td>
                            {bp.outliers && bp.outliers.length > 0 ? (
                              <span className="badge bg-warning text-dark">
                                {bp.outliers.length} outliers beyond 1.5x IQR
                              </span>
                            ) : (
                              <span className="badge bg-light text-muted border">None</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Grouped Breakdown Tool */}
              <div className="card shadow-sm border-0 mb-4 bg-white">
                <div className="card-header bg-white py-3">
                  <h5 className="fw-bold mb-0">
                    <i className="bi bi-pie-chart-fill text-warning me-2"></i>Grouped Bivariate Breakdown
                  </h5>
                  <span className="text-muted small">Compare central tendencies and spread of continuous features across categorical groups.</span>
                </div>
                <div className="card-body">
                  <div className="row g-3 mb-3">
                    <div className="col-md-6">
                      <label className="form-label small fw-semibold">Continuous Variable</label>
                      <select
                        className="form-select form-select-sm"
                        value={selectedNumVar}
                        onChange={(e) => handleGroupedChange(e.target.value, selectedCatVar)}
                      >
                        {numericAnalyticColumns(columns).map((c) => (
                          <option key={c.columnName} value={c.columnName}>{c.columnName}</option>
                        ))}
                      </select>
                    </div>
                    <div className="col-md-6">
                      <label className="form-label small fw-semibold">Grouping Categorical Variable</label>
                      <select
                        className="form-select form-select-sm"
                        value={selectedCatVar}
                        onChange={(e) => handleGroupedChange(selectedNumVar, e.target.value)}
                      >
                        {categoricalAnalyticColumns(columns).map((c) => (
                          <option key={c.columnName} value={c.columnName}>{c.columnName}</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  {groupedSummary.length === 0 ? (
                    <p className="text-muted small mb-0">Select variables above to generate group-level summary metrics.</p>
                  ) : (
                    <div className="table-responsive">
                      <table className="table table-hover align-middle mb-0 small">
                        <thead className="table-light">
                          <tr>
                            <th>Group Category</th>
                            <th>Sample Count (N)</th>
                            <th>Mean</th>
                            <th>Std Dev</th>
                            <th>Median</th>
                            <th>Min</th>
                            <th>Max</th>
                          </tr>
                        </thead>
                        <tbody>
                          {groupedSummary.map((grp) => (
                            <tr key={grp.groupName}>
                              <td className="fw-bold text-dark">{grp.groupName}</td>
                              <td>{grp.count}</td>
                              <td className="fw-semibold text-primary">{grp.mean?.toFixed(2)}</td>
                              <td>{grp.stdDev?.toFixed(2)}</td>
                              <td>{grp.median?.toFixed(2)}</td>
                              <td>{grp.min?.toFixed(2)}</td>
                              <td>{grp.max?.toFixed(2)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* Tab 3: Quality Audit */}
      {activeTab === 'quality' && (
        <div>
          {/* Quality Scores breakdown */}
          <div className="row g-3 mb-4">
            <div className="col-md-3">
              <div className="card border-0 shadow-sm p-3 text-center bg-white">
                <span className="text-muted small fw-semibold">MISSING VALUE SCORE</span>
                <h4 className="fw-bold my-1 text-primary">{quality.missingValueScore?.toFixed(1)}</h4>
                <div className="progress" style={{ height: '5px' }}>
                  <div className="progress-bar bg-primary" style={{ width: `${quality.missingValueScore}%` }}></div>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-0 shadow-sm p-3 text-center bg-white">
                <span className="text-muted small fw-semibold">DEDUPLICATION SCORE</span>
                <h4 className="fw-bold my-1 text-success">{quality.duplicateScore?.toFixed(1)}</h4>
                <div className="progress" style={{ height: '5px' }}>
                  <div className="progress-bar bg-success" style={{ width: `${quality.duplicateScore}%` }}></div>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-0 shadow-sm p-3 text-center bg-white">
                <span className="text-muted small fw-semibold">OUTLIER SCORE</span>
                <h4 className="fw-bold my-1 text-info">{quality.outlierScore?.toFixed(1)}</h4>
                <div className="progress" style={{ height: '5px' }}>
                  <div className="progress-bar bg-info" style={{ width: `${quality.outlierScore}%` }}></div>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-0 shadow-sm p-3 text-center bg-white">
                <span className="text-muted small fw-semibold">CONSISTENCY SCORE</span>
                <h4 className="fw-bold my-1 text-warning">{quality.consistencyScore?.toFixed(1)}</h4>
                <div className="progress" style={{ height: '5px' }}>
                  <div className="progress-bar bg-warning" style={{ width: `${quality.consistencyScore}%` }}></div>
                </div>
              </div>
            </div>
          </div>

          {/* Audit Issues List */}
          <div className="card shadow-sm border-0">
            <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center">
              <h5 className="fw-bold mb-0">Detected Anomalies & Recommendations</h5>
              <span className="badge bg-secondary">{issues.length} detected</span>
            </div>
            {issues.length === 0 ? (
              <div className="card-body text-center py-5">
                <i className="bi bi-shield-check display-4 text-success mb-2 d-block"></i>
                <h6 className="fw-bold text-success">Clean Dataset Structure</h6>
                <p className="text-muted small mb-0">No critical anomalies or data inconsistencies detected.</p>
              </div>
            ) : (
              <div className="list-group list-group-flush">
                {issues.map((issue, idx) => (
                  <div key={issue.id || idx} className="list-group-item p-3">
                    <div className="d-flex align-items-center justify-content-between mb-1">
                      <div className="d-flex align-items-center gap-2">
                        {getSeverityBadge(issue.severity)}
                        <span className="fw-bold text-dark">{issue.issueType}</span>
                        {issue.columnName && (
                          <span className="badge bg-light text-dark border">
                            Column: {issue.columnName}
                          </span>
                        )}
                      </div>
                      {issue.affectedRows > 0 && (
                        <span className="text-muted small">
                          Affected Rows: <strong>{issue.affectedRows}</strong>
                        </span>
                      )}
                    </div>
                    <p className="mb-1 text-secondary small">{issue.description}</p>
                    <div className="bg-light p-2 rounded small text-dark mt-2 border-start border-3 border-primary">
                      <strong className="text-primary"><i className="bi bi-lightbulb me-1"></i>Recommendation: </strong>
                      {issue.recommendation}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
      </WorkflowNavigator>
    </div>
  );
};

export default DatasetDetailPage;
