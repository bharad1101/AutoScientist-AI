import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useLocation, useNavigate, Link } from 'react-router-dom';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';
import WorkflowNavigator from '../components/WorkflowNavigator';
import ModelEvaluationPanel from '../components/ModelEvaluationPanel';
import { useWorkflow } from '../context/WorkflowContext';
import { analyticColumns } from '../utils/columns';
import { Bar, Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  LineElement,
  PointElement,
  BarController,
  LineController,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';

ChartJS.register(CategoryScale, LinearScale, BarElement, LineElement, PointElement, BarController, LineController, Title, Tooltip, Legend);

export default function AutoMlPage() {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { datasetId: workflowDatasetId, selectDataset, refreshWorkflowStatus } = useWorkflow();

  const currentStage = location.pathname.includes('/xai') ? 'xai' : 'ml';
  const effectiveId = id || workflowDatasetId;

  const [datasets, setDatasets] = useState([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState(effectiveId || '');
  const [selectedDataset, setSelectedDataset] = useState(null);
  const [targetColumn, setTargetColumn] = useState('');
  const [problemType, setProblemType] = useState('AUTO');
  const [sensitiveColumn, setSensitiveColumn] = useState('');
  const [initialLoading, setInitialLoading] = useState(true);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);

  // What-If Simulator state
  const [whatIfInputs, setWhatIfInputs] = useState({});
  const [whatIfResult, setWhatIfResult] = useState(null);
  const [whatIfLoading, setWhatIfLoading] = useState(false);
  const [whatIfSchema, setWhatIfSchema] = useState(null);
  const [whatIfSchemaError, setWhatIfSchemaError] = useState('');

  // ML Benchmark Feature Response state
  const [selectedBenchmarkFeature, setSelectedBenchmarkFeature] = useState('');
  const [featureEffect, setFeatureEffect] = useState(null);
  const [featureEffectLoading, setFeatureEffectLoading] = useState(false);
  const [featureEffectError, setFeatureEffectError] = useState('');

  const loadFeatureEffect = useCallback(async (dsId, featureName) => {
    if (!dsId) return;
    try {
      setFeatureEffectLoading(true);
      setFeatureEffectError('');
      const res = await analysisService.getFeatureEffect(dsId, featureName);
      if (res && res.data) {
        setFeatureEffect(res.data);
        if (res.data.featureName) {
          setSelectedBenchmarkFeature(res.data.featureName);
        }
      } else {
        setFeatureEffect(null);
      }
    } catch (err) {
      console.error('Failed to load feature effect:', err);
      setFeatureEffectError(
        err.response?.data?.message || err.message || 'Unable to compute feature response graph.'
      );
      setFeatureEffect(null);
    } finally {
      setFeatureEffectLoading(false);
    }
  }, []);

  // The what-if form must mirror the model that was actually trained: the raw
  // columns, their types, and the reference (median/mode) values.
  const loadWhatIfSchema = useCallback(async (dsId) => {
    if (!dsId) return;
    try {
      setWhatIfSchemaError('');
      const res = await analysisService.getWhatIfSchema(dsId);
      const features = res?.data?.features || [];
      setWhatIfSchema(features);
      const initial = {};
      features.forEach((f) => {
        initial[f.name] = f.referenceValue ?? '';
      });
      setWhatIfInputs(initial);
    } catch (err) {
      setWhatIfSchema([]);
      setWhatIfSchemaError(
        err.response?.data?.message ||
          'The what-if input schema is not available. Train an AutoML model on this dataset first.'
      );
    }
  }, []);

  // 1. Load active dataset directly from route parameter or workflow context
  const loadActiveDataset = useCallback(async (dsId) => {
    if (!dsId) {
      setInitialLoading(false);
      return;
    }
    setInitialLoading(true);
    setError(null);
    try {
      setSelectedDatasetId(dsId);
      if (selectDataset) selectDataset(dsId);

      const [dsRes, mlRes] = await Promise.all([
        datasetService.getDataset(dsId),
        analysisService.getLatestAutoMl(dsId).catch(() => null),
      ]);

      if (dsRes && dsRes.data) {
        const ds = dsRes.data;
        setSelectedDataset(ds);

        const cols = ds.columns || [];
        const nonIdCols = analyticColumns(cols);
        const defaultTarget =
          nonIdCols.length > 0 ? nonIdCols[nonIdCols.length - 1].columnName : '';

        setTargetColumn(defaultTarget);

        // Auto-detect problem type from default target
        const targetMeta = cols.find((c) => c.columnName === defaultTarget);
        if (targetMeta) {
          if (targetMeta.dataType === 'NUMERIC' && targetMeta.uniqueCount > 10) {
            setProblemType('REGRESSION');
          } else {
            setProblemType('CLASSIFICATION');
          }
        }

        // Auto-detect suggested sensitive column
        const sens = cols.find((c) => {
          const n = c.columnName.toLowerCase();
          return (
            n.includes('gender') ||
            n.includes('sex') ||
            n.includes('race') ||
            n.includes('ethnicity') ||
            n.includes('tutoring') ||
            n.includes('smoker')
          );
        });
        if (sens) {
          setSensitiveColumn(sens.columnName);
        }
      }

      if (mlRes && mlRes.data) {
        setResults(mlRes.data);
        if (mlRes.data.targetColumn) setTargetColumn(mlRes.data.targetColumn);
        if (mlRes.data.problemType) setProblemType(mlRes.data.problemType);
        await Promise.all([
          loadWhatIfSchema(dsId),
          loadFeatureEffect(dsId),
        ]);
      }
    } catch (err) {
      console.error('Failed to load active dataset details:', err);
      setError(
        err.response?.data?.message ||
          err.message ||
          'Unable to load the active dataset from server.'
      );
    } finally {
      setInitialLoading(false);
    }
  }, [selectDataset, loadWhatIfSchema, loadFeatureEffect]);

  useEffect(() => {
    if (effectiveId) {
      loadActiveDataset(effectiveId);
    } else {
      // If no ID in route or context, try fetching available datasets
      datasetService
        .listDatasets()
        .then((res) => {
          if (res.data && res.data.length > 0) {
            setDatasets(res.data);
            loadActiveDataset(res.data[0].id);
          } else {
            setInitialLoading(false);
          }
        })
        .catch(() => {
          setInitialLoading(false);
        });
    }

    // Background fetch dataset list for switcher if multiple exist
    datasetService
      .listDatasets()
      .then((res) => {
        if (res.data) setDatasets(res.data);
      })
      .catch(() => {});
  }, [effectiveId, loadActiveDataset]);

  const handleTargetChange = (newTarget) => {
    setTargetColumn(newTarget);
    if (!selectedDataset || !selectedDataset.columns) return;
    const colMeta = selectedDataset.columns.find((c) => c.columnName === newTarget);
    if (colMeta) {
      if (colMeta.dataType === 'NUMERIC' && colMeta.uniqueCount > 10) {
        setProblemType('REGRESSION');
      } else {
        setProblemType('CLASSIFICATION');
      }
    }
  };

  const handleTrainAutoMl = async (e) => {
    e.preventDefault();
    if (!selectedDatasetId || !targetColumn) return;

    setLoading(true);
    setError(null);
    try {
      const res = await analysisService.trainAutoMl(selectedDatasetId, {
        targetColumn,
        problemType,
        sensitiveColumn: sensitiveColumn || null,
        experimentName: `AutoML Benchmark - ${targetColumn}`,
      });
      setResults(res.data);
      refreshWorkflowStatus();
      await Promise.all([
        loadWhatIfSchema(selectedDatasetId),
        loadFeatureEffect(selectedDatasetId),
      ]);
    } catch (err) {
      setError(err.response?.data?.message || 'AutoML training failed.');
    } finally {
      setLoading(false);
    }
  };

  const handleWhatIfSubmit = async (e) => {
    e.preventDefault();
    setWhatIfLoading(true);
    try {
      const res = await analysisService.simulateWhatIf(selectedDatasetId, {
        targetColumn,
        featureValues: whatIfInputs,
      });
      setWhatIfResult(res.data);
    } catch (err) {
      setError('What-If simulation failed.');
    } finally {
      setWhatIfLoading(false);
    }
  };

  const handleBenchmarkFeatureChange = (featureName) => {
    setSelectedBenchmarkFeature(featureName);
    loadFeatureEffect(selectedDatasetId, featureName);
  };

  // Fairness cohort comparison: real per-group rates computed on the validation slice.
  const fairnessChartData = useMemo(() => {
    const fairness = results?.fairness;
    if (!fairness) return null;
    const positiveRates = fairness.groupPositiveRates || {};
    const errorRates = fairness.groupErrorRates || {};
    const groups = Object.keys(positiveRates);
    if (!groups.length) return null;
    const isRegression = results?.problemType === 'REGRESSION';
    return {
      labels: groups,
      datasets: [
        {
          type: 'bar',
          label: isRegression ? 'Mean prediction' : 'Positive rate',
          data: groups.map((g) => positiveRates[g]),
          backgroundColor: 'rgba(37, 99, 235, 0.7)',
          borderRadius: 3,
          order: 2,
        },
        {
          type: 'line',
          label: isRegression ? 'Mean absolute error' : 'Error rate',
          data: groups.map((g) => errorRates[g] ?? 0),
          borderColor: 'rgba(239, 68, 68, 0.9)',
          backgroundColor: 'rgba(239, 68, 68, 0.9)',
          borderWidth: 2,
          pointRadius: 4,
          tension: 0,
          order: 1,
        },
      ],
    };
  }, [results]);

  const fairnessSampleNote = useMemo(() => {
    const sizes = results?.fairness?.groupSampleSizes;
    if (!sizes || !Object.keys(sizes).length) return '';
    return `Validation rows per cohort: ${Object.entries(sizes)
      .map(([g, n]) => `${g}=${n}`)
      .join(', ')}.`;
  }, [results]);

  // Ranked model comparison: validation vs. holdout test performance per candidate.
  const modelComparison = useMemo(() => {
    const models = results?.models || [];
    if (!models.length) return null;
    const isRegression = results?.problemType === 'REGRESSION';
    const primaryKey = isRegression ? 'r2' : 'f1';

    const rows = models
      .map((m) => ({
        name: m.modelName,
        algorithm: m.algorithm,
        isBest: !!m.isBestModel,
        val: typeof m.valMetrics?.[primaryKey] === 'number' ? m.valMetrics[primaryKey] : null,
        test: typeof m.testMetrics?.[primaryKey] === 'number' ? m.testMetrics[primaryKey] : null,
        cvMean:
          typeof m.evaluation?.crossValidation?.mean === 'number' ? m.evaluation.crossValidation.mean : null,
        cvStd:
          typeof m.evaluation?.crossValidation?.std === 'number' ? m.evaluation.crossValidation.std : null,
        secondary: isRegression
          ? typeof m.valMetrics?.rmse === 'number'
            ? m.valMetrics.rmse
            : null
          : typeof m.valMetrics?.accuracy === 'number'
            ? m.valMetrics.accuracy
            : null,
      }))
      .filter((r) => r.val != null || r.test != null);

    if (!rows.length) return null;

    // Rank by the primary validation metric (descending).
    rows.sort((a, b) => (b.val ?? -Infinity) - (a.val ?? -Infinity));
    rows.forEach((r, i) => {
      r.rank = i + 1;
    });

    const hasCv = rows.some((r) => r.cvMean != null);
    const metricLabel = isRegression ? 'R² (test set)' : 'F1 score (test set)';

    return {
      isRegression,
      metricLabel,
      hasCv,
      rows,
      data: {
        labels: rows.map((r) => `#${r.rank} ${r.name}`),
        datasets: [
          {
            label: 'Validation metric',
            data: rows.map((r) => r.val),
            backgroundColor: rows.map((r) =>
              r.isBest ? 'rgba(16, 185, 129, 0.85)' : 'rgba(37, 99, 235, 0.7)'
            ),
            borderRadius: 3,
          },
          {
            label: 'Test metric',
            data: rows.map((r) => r.test),
            backgroundColor: rows.map((r) =>
              r.isBest ? 'rgba(16, 185, 129, 0.45)' : 'rgba(37, 99, 235, 0.35)'
            ),
            borderColor: 'rgba(37, 99, 235, 0.9)',
            borderWidth: 1,
            borderRadius: 3,
          },
          ...(hasCv
            ? [
                {
                  label: 'Cross-validation mean',
                  data: rows.map((r) => r.cvMean),
                  backgroundColor: 'rgba(249, 115, 22, 0.6)',
                  borderRadius: 3,
                },
              ]
            : []),
        ],
      },
    };
  }, [results]);

  // Chart configuration for Permutation Feature Importance
  const featureChartData = results?.featureImportances
    ? {
        labels: results.featureImportances.map((f) => f.featureName),
        datasets: [
          {
            label: 'Relative Importance (%)',
            data: results.featureImportances.map((f) => f.relativePercentage),
            backgroundColor: 'rgba(59, 130, 246, 0.75)',
            borderColor: 'rgb(37, 99, 235)',
            borderWidth: 1,
            borderRadius: 4,
          },
        ],
      }
    : null;

  const availableFeatures = useMemo(() => {
    if (results?.featureImportances && results.featureImportances.length > 0) {
      return results.featureImportances.map((f) => f.featureName);
    }
    if (whatIfSchema && whatIfSchema.length > 0) {
      return whatIfSchema.map((f) => f.name);
    }
    if (selectedDataset?.columns) {
      return analyticColumns(selectedDataset.columns)
        .filter((c) => c.columnName !== targetColumn)
        .map((c) => c.columnName);
    }
    return [];
  }, [results, whatIfSchema, selectedDataset, targetColumn]);

  const featureEffectChartData = useMemo(() => {
    if (!featureEffect || !featureEffect.supported) return null;

    if (featureEffect.kind === 'NUMERIC' && featureEffect.points && featureEffect.points.length > 0) {
      const labels = featureEffect.points.map((p) => p.x);
      const isClassification = featureEffect.problemType === 'CLASSIFICATION';
      const hasProba = featureEffect.points.some((p) => p.probability != null);

      const datasets = [
        {
          label: `Model Output (${featureEffect.targetColumn})`,
          data: featureEffect.points.map((p) => p.prediction),
          borderColor: 'rgb(13, 110, 253)',
          backgroundColor: 'rgba(13, 110, 253, 0.1)',
          fill: true,
          tension: 0.25,
          pointRadius: 3,
          pointHoverRadius: 6,
          yAxisID: 'y',
        },
      ];

      if (isClassification && hasProba) {
        datasets.push({
          label: 'Class Probability',
          data: featureEffect.points.map((p) => p.probability),
          borderColor: 'rgb(25, 135, 84)',
          backgroundColor: 'transparent',
          borderDash: [5, 5],
          tension: 0.25,
          pointRadius: 2,
          yAxisID: 'y1',
        });
      }

      return {
        type: 'line',
        data: { labels, datasets },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: { mode: 'index', intersect: false },
          plugins: {
            legend: { position: 'top' },
            tooltip: {
              callbacks: {
                label: (ctx) => {
                  if (ctx.dataset.yAxisID === 'y1') {
                    return `Probability: ${(ctx.parsed.y * 100).toFixed(1)}%`;
                  }
                  return `Predicted ${featureEffect.targetColumn}: ${ctx.parsed.y}`;
                },
              },
            },
          },
          scales: {
            x: {
              title: {
                display: true,
                text: `${featureEffect.featureName} (Evaluated Grid Range)`,
              },
            },
            y: {
              title: {
                display: true,
                text: `Model Output (${featureEffect.targetColumn})`,
              },
            },
            ...(isClassification && hasProba
              ? {
                  y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    min: 0,
                    max: 1,
                    title: { display: true, text: 'Probability' },
                    grid: { drawOnChartArea: false },
                  },
                }
              : {}),
          },
        },
      };
    }

    if (featureEffect.kind === 'CATEGORICAL' && featureEffect.categories && featureEffect.categories.length > 0) {
      const labels = featureEffect.categories.map((c) => c.category);
      return {
        type: 'bar',
        data: {
          labels,
          datasets: [
            {
              label: `Model Output (${featureEffect.targetColumn})`,
              data: featureEffect.categories.map((c) => c.prediction),
              backgroundColor: 'rgba(13, 110, 253, 0.75)',
              borderColor: 'rgb(13, 110, 253)',
              borderWidth: 1,
              borderRadius: 4,
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: (ctx) => `Predicted ${featureEffect.targetColumn}: ${ctx.parsed.y}`,
              },
            },
          },
          scales: {
            x: {
              title: {
                display: true,
                text: `Category (${featureEffect.featureName})`,
              },
            },
            y: {
              title: {
                display: true,
                text: `Model Output (${featureEffect.targetColumn})`,
              },
            },
          },
        },
      };
    }

    return null;
  }, [featureEffect]);

  const evaluationModel =
    results?.bestModel?.evaluation
      ? results.bestModel
      : results?.models?.find((m) => m.evaluation) || null;

  const candidateTargetCols = analyticColumns(selectedDataset?.columns);

  if (initialLoading) {
    return (
      <div className="container py-5 text-center">
        <div className="spinner-border text-primary mb-3" role="status"></div>
        <p className="text-muted fw-semibold">Loading active dataset and model state...</p>
      </div>
    );
  }

  return (
    <div className="container py-4">
      <WorkflowNavigator currentStage={currentStage}>
        {/* Header Banner */}
        <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
          <div>
            <h2 className="fw-bold mb-1">
              {currentStage === 'xai' ? (
                <>
                  <i className="bi bi-eye-fill text-warning me-2"></i>
                  Explainability (XAI) & Algorithmic Fairness
                </>
              ) : (
                <>
                  <i className="bi bi-cpu-fill text-primary me-2"></i>
                  AutoML Benchmarking & Model Evaluation
                </>
              )}
            </h2>
            <p className="text-muted mb-0 small">
              {currentStage === 'xai'
                ? 'Permutation feature importance, demographic parity audits, and counterfactual scenario simulations.'
                : 'Leak-free 70/15/15 train/val/test splits, multi-algorithm benchmarking, and empirical leaderboard ranking.'}
            </p>
          </div>

          {selectedDataset && (
            <div className="d-flex align-items-center gap-2">
              <span className="badge bg-light text-dark border p-2">
                <i className="bi bi-database me-1 text-primary"></i>
                <strong>{selectedDataset.name}</strong> ({selectedDataset.rowCount} rows &bull;{' '}
                {selectedDataset.columnCount} columns)
              </span>
              {datasets.length > 1 && (
                <select
                  className="form-select form-select-sm"
                  style={{ width: '160px' }}
                  value={selectedDatasetId}
                  onChange={(e) => {
                    const newId = e.target.value;
                    navigate(`/datasets/${newId}/${currentStage}`);
                  }}
                >
                  {datasets.map((d) => (
                    <option key={d.id} value={d.id}>
                      Switch: {d.name}
                    </option>
                  ))}
                </select>
              )}
            </div>
          )}
        </div>

        {/* Error Alert with Retry */}
        {error && (
          <div className="alert alert-danger shadow-sm d-flex justify-content-between align-items-center mb-4" role="alert">
            <div className="d-flex align-items-center gap-2">
              <i className="bi bi-exclamation-triangle-fill fs-5"></i>
              <div>
                <strong>Error:</strong> {error}
              </div>
            </div>
            <div className="d-flex gap-2">
              <button
                type="button"
                className="btn btn-sm btn-outline-danger"
                onClick={() => loadActiveDataset(selectedDatasetId || effectiveId)}
              >
                <i className="bi bi-arrow-clockwise me-1"></i> Retry
              </button>
              <button type="button" className="btn-close" onClick={() => setError(null)}></button>
            </div>
          </div>
        )}

        {/* XAI PREREQUISITE VIEW: If on XAI stage and no model is trained */}
        {currentStage === 'xai' && !results ? (
          <div className="card border-warning border-2 bg-warning bg-opacity-10 shadow-sm p-5 mb-4 text-center">
            <div className="text-warning fs-1 mb-2">
              <i className="bi bi-lightbulb-off-fill"></i>
            </div>
            <h4 className="fw-bold text-dark mb-2">Train a Model First</h4>
            <p className="text-secondary mb-4 mx-auto" style={{ maxWidth: '620px' }}>
              XAI requires a successfully trained model before explanations, permutation feature importance,
              and demographic fairness audits can be generated.
            </p>
            <div>
              <Link
                to={`/datasets/${selectedDatasetId || effectiveId}/ml`}
                className="btn btn-primary fw-semibold px-4 py-2 shadow-sm"
              >
                Go to Machine Learning <i className="bi bi-arrow-right ms-1"></i>
              </Link>
            </div>
          </div>
        ) : null}

        {/* STAGE 9: AutoML Training Configuration Card (shown when on ML stage) */}
        {currentStage === 'ml' && (
          <div className="card shadow-sm border-0 mb-4">
            <div className="card-header bg-white py-3 border-0">
              <h5 className="fw-bold mb-0">
                <i className="bi bi-gear-wide-connected text-primary me-2"></i>
                AutoML Training Configuration
              </h5>
            </div>
            <div className="card-body p-4">
              <form onSubmit={handleTrainAutoMl}>
                <div className="row g-3">
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Target Variable (Y)</label>
                    <select
                      className="form-select"
                      value={targetColumn}
                      onChange={(e) => handleTargetChange(e.target.value)}
                      disabled={loading || !selectedDataset}
                    >
                      {candidateTargetCols.map((c) => (
                        <option key={c.columnName} value={c.columnName}>
                          {c.columnName} ({c.dataType})
                        </option>
                      ))}
                    </select>
                    <small className="text-muted d-block mt-1">
                      Identifier variables are automatically filtered from candidate targets.
                    </small>
                  </div>

                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Problem Type</label>
                    <select
                      className="form-select"
                      value={problemType}
                      onChange={(e) => setProblemType(e.target.value)}
                      disabled={loading}
                    >
                      <option value="AUTO">Auto-Detect from Target</option>
                      <option value="REGRESSION">Regression (Continuous Numerical)</option>
                      <option value="CLASSIFICATION">Classification (Categorical / Binary)</option>
                    </select>
                    <small className="text-muted d-block mt-1">
                      Determined automatically based on target variable distribution.
                    </small>
                  </div>

                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Sensitive Column (Fairness Audit)</label>
                    <select
                      className="form-select"
                      value={sensitiveColumn}
                      onChange={(e) => setSensitiveColumn(e.target.value)}
                      disabled={loading || !selectedDataset}
                    >
                      <option value="">Auto-Detect / None</option>
                      {selectedDataset?.columns?.map((c) => (
                        <option key={c.columnName} value={c.columnName}>
                          {c.columnName} ({c.dataType})
                        </option>
                      ))}
                    </select>
                    <small className="text-muted d-block mt-1">
                      Audits EEOC 80% Four-Fifths rule across groups.
                    </small>
                  </div>
                </div>

                <div className="mt-4 d-flex justify-content-end">
                  <button
                    type="submit"
                    className="btn btn-primary px-4 py-2 fw-semibold"
                    disabled={loading || !selectedDatasetId || !targetColumn}
                  >
                    {loading ? (
                      <>
                        <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                        Benchmarking Models (70/15/15)...
                      </>
                    ) : (
                      <>
                        <i className="bi bi-play-circle-fill me-2"></i>
                        Run AutoML Benchmark
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* RESULTS VIEW: Rendered if models have been benchmarked */}
        {results && (
          <>
            {/* Scientific Summary Banner */}
            <div className="alert alert-primary shadow-sm border-0 mb-4 p-4">
              <h5 className="alert-heading fw-bold mb-2">
                <i className="bi bi-journal-check me-2"></i>
                Empirical AutoML Summary
              </h5>
              <p className="mb-0 fs-6">{results.scientificSummary}</p>
            </div>

            {/* Candidate Model Leaderboard (Shown on ML and XAI) */}
            <div className="row g-4 mb-4">
              <div className="col-lg-7">
                <div className="card shadow-sm border-0 h-100">
                  <div className="card-header bg-white py-3 border-0">
                    <h5 className="fw-bold mb-0">
                      <i className="bi bi-trophy-fill text-warning me-2"></i>
                      Candidate Model Benchmark Leaderboard
                    </h5>
                    <small className="text-muted">
                      Evaluated on holdout validation & test splits without target leakage.
                    </small>
                  </div>
                  {modelComparison && (
                    <div className="card-body p-4 border-bottom">
                      <h6 className="fw-bold small text-uppercase text-muted mb-2">
                        <i className="bi bi-bar-chart-fill me-1"></i>Ranked Model Comparison
                      </h6>
                      <div style={{ height: `${Math.max(220, modelComparison.rows.length * 58)}px` }}>
                        <Bar
                          data={modelComparison.data}
                          options={{
                            indexAxis: 'y',
                            responsive: true,
                            maintainAspectRatio: false,
                            plugins: {
                              legend: { position: 'bottom' },
                              tooltip: {
                                callbacks: {
                                  afterBody: (items) => {
                                    const r = modelComparison.rows[items[0]?.dataIndex];
                                    if (!r) return [];
                                    const lines = [`Algorithm: ${r.algorithm}`];
                                    if (modelComparison.isRegression) {
                                      if (r.secondary != null) lines.push(`Validation RMSE: ${r.secondary.toFixed(3)}`);
                                    } else if (r.secondary != null) {
                                      lines.push(`Validation accuracy: ${(r.secondary * 100).toFixed(2)}%`);
                                    }
                                    if (r.cvMean != null) {
                                      lines.push(
                                        `CV mean: ${r.cvMean.toFixed(4)}${
                                          r.cvStd != null ? ` ± ${r.cvStd.toFixed(4)}` : ''
                                        }`
                                      );
                                    }
                                    return lines;
                                  },
                                },
                              },
                            },
                            scales: {
                              x: {
                                title: { display: true, text: modelComparison.metricLabel },
                                ...(modelComparison.isRegression ? {} : { min: 0, max: 1 }),
                              },
                              y: { title: { display: true, text: 'Candidate model (ranked)' } },
                            },
                          }}
                        />
                      </div>
                      <p className="small text-muted mb-0 mt-2">
                        <i className="bi bi-info-circle me-1"></i>
                        Models are ranked by the primary validation metric (
                        {modelComparison.isRegression ? 'R²' : 'F1'}). The green bars mark the selected best model.
                        {modelComparison.hasCv
                          ? ' Cross-validation means come from the seeded k-fold run for that candidate.'
                          : ' Cross-validation means are shown only when a candidate recorded a completed k-fold run.'}
                      </p>
                    </div>
                  )}
                  <div className="card-body p-0">
                    <div className="table-responsive">
                      <table className="table table-hover align-middle mb-0">
                        <thead className="table-light">
                          <tr>
                            <th>Rank</th>
                            <th>Model</th>
                            <th>Algorithm</th>
                            <th>Validation Metric</th>
                            <th>Test Metric</th>
                            <th>Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          {results.models?.map((m, idx) => (
                            <tr key={m.id || idx} className={m.isBestModel ? 'table-primary bg-opacity-25' : ''}>
                              <td className="fw-bold text-center">{idx + 1}</td>
                              <td>
                                <span className="fw-semibold">{m.modelName}</span>
                              </td>
                              <td>
                                <span className="badge bg-light text-dark border">{m.algorithm}</span>
                              </td>
                              <td>
                                {results.problemType === 'REGRESSION' ? (
                                  <span>
                                    R² = <strong>{m.valMetrics?.r2?.toFixed(3) ?? 'N/A'}</strong> (RMSE ={' '}
                                    {m.valMetrics?.rmse?.toFixed(2) ?? 'N/A'})
                                  </span>
                                ) : (
                                  <span>
                                    F1 = <strong>{m.valMetrics?.f1?.toFixed(3) ?? 'N/A'}</strong> (Acc ={' '}
                                    {((m.valMetrics?.accuracy ?? 0) * 100).toFixed(1)}%)
                                  </span>
                                )}
                              </td>
                              <td>
                                {results.problemType === 'REGRESSION' ? (
                                  <span>R² = {m.testMetrics?.r2?.toFixed(3) ?? 'N/A'}</span>
                                ) : (
                                  <span>F1 = {m.testMetrics?.f1?.toFixed(3) ?? 'N/A'}</span>
                                )}
                              </td>
                              <td>
                                {m.isBestModel ? (
                                  <span className="badge bg-success">
                                    <i className="bi bi-star-fill me-1"></i> Best Model
                                  </span>
                                ) : (
                                  <span className="badge bg-secondary">Candidate</span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>

              {/* Fairness Audit Card */}
              <div className="col-lg-5">
                <div className="card shadow-sm border-0 h-100">
                  <div className="card-header bg-white py-3 border-0">
                    <h5 className="fw-bold mb-0">
                      <i className="bi bi-shield-check text-success me-2"></i>
                      Algorithmic Fairness & Bias Audit
                    </h5>
                    <small className="text-muted">
                      EEOC 80% Four-Fifths disparate impact verification
                    </small>
                  </div>
                  <div className="card-body p-4">
                    {results.fairness ? (
                      <div>
                        <div className="d-flex justify-content-between align-items-center mb-3">
                          <span className="text-muted">Sensitive Attribute:</span>
                          <span className="fw-bold text-dark">{results.fairness.sensitiveColumn}</span>
                        </div>

                        <div className="d-flex justify-content-between align-items-center mb-3">
                          <span className="text-muted">Demographic Parity Ratio:</span>
                          <span className="fw-bold fs-5 text-primary">
                            {results.fairness.demographicParityRatio?.toFixed(3)}
                          </span>
                        </div>

                        <div className="d-flex justify-content-between align-items-center mb-3">
                          <span className="text-muted">Four-Fifths (80%) Rule:</span>
                          <span
                            className={`badge ${
                              results.fairness.disparateImpactStatus === 'PASS'
                                ? 'bg-success'
                                : results.fairness.disparateImpactStatus === 'FLAGGED'
                                ? 'bg-danger'
                                : 'bg-secondary'
                            } px-3 py-2`}
                          >
                            {results.fairness.disparateImpactStatus}
                          </span>
                        </div>

                        <div className="alert alert-light border p-3 mt-3">
                          <small className="text-muted d-block fw-semibold mb-1">Audit Finding & Guidance:</small>
                          <small>{results.fairness.recommendation}</small>
                        </div>

                        {fairnessChartData && (
                          <div className="mt-3">
                            <div style={{ height: '220px' }}>
                              <Bar
                                data={fairnessChartData}
                                options={{
                                  responsive: true,
                                  maintainAspectRatio: false,
                                  plugins: { legend: { position: 'bottom' } },
                                  scales: {
                                    x: { title: { display: true, text: `Cohort (${results.fairness.sensitiveColumn})` } },
                                    y: { beginAtZero: true, title: { display: true, text: 'Rate' } },
                                  },
                                }}
                              />
                            </div>
                            <p className="small text-muted mb-0 mt-2">
                              {results.problemType === 'REGRESSION'
                                ? 'Mean predicted outcome per cohort (bars) and mean absolute error (line).'
                                : 'Positive prediction rate per cohort (bars) and error rate (line).'}
                              {fairnessSampleNote ? ` ${fairnessSampleNote}` : ''}
                            </p>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div className="text-center py-4 text-muted">
                        <i className="bi bi-shield-slash fs-2 d-block mb-2"></i>
                        <span>No sensitive demographic attribute specified for fairness audit.</span>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Deep Model Evaluation (ROC/PR, confusion matrix, CV) */}
            {evaluationModel && (
              <ModelEvaluationPanel
                evaluation={evaluationModel.evaluation}
                problemType={results.problemType}
                modelName={evaluationModel.modelName}
              />
            )}

            {/* Explainability (XAI): Permutation Feature Importance */}
            <div className="card shadow-sm border-0 mb-4">
              <div className="card-header bg-white py-3 border-0 d-flex justify-content-between align-items-center">
                <div>
                  <h5 className="fw-bold mb-0">
                    <i className="bi bi-lightbulb-fill text-warning me-2"></i>
                    Explainability (XAI): Permutation Feature Importance
                  </h5>
                  <small className="text-muted">
                    Methodology: Permutation feature importance on test partition (Leak-free, not SHAP)
                  </small>
                </div>
                <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 px-2 py-1">
                  Empirical XAI
                </span>
              </div>
              <div className="card-body p-4">
                <div className="row g-4 align-items-center">
                  <div className="col-lg-6">
                    {featureChartData && (
                      <div style={{ height: '320px' }}>
                        <Bar
                          data={featureChartData}
                          options={{
                            responsive: true,
                            maintainAspectRatio: false,
                            indexAxis: 'y',
                            plugins: {
                              legend: { display: false },
                              tooltip: {
                                callbacks: {
                                  label: (ctx) => `Importance: ${ctx.parsed.x}%`,
                                },
                              },
                            },
                            scales: {
                              x: {
                                title: { display: true, text: 'Relative Contribution (%)' },
                                max: 100,
                              },
                            },
                          }}
                        />
                      </div>
                    )}
                  </div>

                  <div className="col-lg-6">
                    <div className="table-responsive">
                      <table className="table table-sm align-middle">
                        <thead className="table-light">
                          <tr>
                            <th>Rank</th>
                            <th>Feature</th>
                            <th>Importance</th>
                            <th>Direction</th>
                            <th>Scientific Interpretation</th>
                          </tr>
                        </thead>
                        <tbody>
                          {results.featureImportances?.map((f) => (
                            <tr
                              key={f.featureName}
                              style={{ cursor: 'pointer' }}
                              className={selectedBenchmarkFeature === f.featureName ? 'table-primary bg-opacity-25' : ''}
                              onClick={() => handleBenchmarkFeatureChange(f.featureName)}
                              title={`Click to inspect benchmark model response for ${f.featureName}`}
                            >
                              <td className="fw-bold text-center">#{f.rank}</td>
                              <td className="fw-semibold text-primary">
                                {f.featureName}
                                {selectedBenchmarkFeature === f.featureName && (
                                  <span className="badge bg-primary ms-2 small">Selected</span>
                                )}
                              </td>
                              <td>{f.relativePercentage?.toFixed(1)}%</td>
                              <td>
                                <span
                                  className={`badge ${
                                    f.direction === 'POSITIVE'
                                      ? 'bg-success'
                                      : f.direction === 'NEGATIVE'
                                      ? 'bg-danger'
                                      : 'bg-secondary'
                                  }`}
                                >
                                  {f.direction}
                                </span>
                              </td>
                              <td>
                                <small className="text-muted">{f.interpretation}</small>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Feature-Level Model Benchmark Response Curve */}
            <div className="card shadow-sm border-0 mb-4" id="benchmarkFeatureSection" data-testid="benchmark-feature-section">
              <div className="card-header bg-white py-3 border-0 d-flex flex-wrap justify-content-between align-items-center gap-2">
                <div>
                  <h5 className="fw-bold mb-0">
                    <i className="bi bi-graph-up text-primary me-2"></i>
                    ML Benchmark: Feature Response & Effect Curve
                  </h5>
                  <small className="text-muted">
                    Marginal conditional response of the benchmarked best model across the empirical range of the selected feature.
                  </small>
                </div>
                <div className="d-flex align-items-center gap-2">
                  <label htmlFor="benchmarkFeatureSelect" className="small fw-semibold text-secondary mb-0">
                    Select Feature:
                  </label>
                  <select
                    id="benchmarkFeatureSelect"
                    className="form-select form-select-sm"
                    style={{ minWidth: '180px' }}
                    value={selectedBenchmarkFeature}
                    onChange={(e) => handleBenchmarkFeatureChange(e.target.value)}
                    aria-label="Select feature to inspect model response"
                  >
                    {availableFeatures.map((feat) => (
                      <option key={feat} value={feat}>
                        {feat}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="card-body p-4">
                {featureEffectLoading ? (
                  <div className="text-center py-5" data-testid="benchmark-feature-loading">
                    <div className="spinner-border text-primary spinner-border-sm me-2" role="status"></div>
                    <span className="text-muted small">Evaluating benchmark model response curve...</span>
                  </div>
                ) : featureEffectError ? (
                  <div className="alert alert-warning mb-0" role="alert" data-testid="benchmark-feature-error">
                    <i className="bi bi-exclamation-triangle me-2"></i>
                    {featureEffectError}
                  </div>
                ) : !featureEffect || !featureEffect.supported ? (
                  <div className="p-4 text-center bg-light rounded border" data-testid="benchmark-feature-empty">
                    <i className="bi bi-info-circle text-muted fs-4 d-block mb-2"></i>
                    <p className="text-muted small mb-0">
                      {featureEffect?.unsupportedReason || 'Select a feature above to inspect its benchmark prediction curve.'}
                    </p>
                  </div>
                ) : (
                  <div>
                    <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
                      <div>
                        <span className="badge bg-light text-dark border me-2">
                          <strong>Feature:</strong> {featureEffect.featureName} ({featureEffect.kind})
                        </span>
                        <span className="badge bg-light text-dark border me-2">
                          <strong>Target:</strong> {featureEffect.targetColumn} ({featureEffect.problemType})
                        </span>
                        {featureEffect.referenceValue != null && (
                          <span className="badge bg-secondary bg-opacity-10 text-secondary border">
                            Baseline Reference: {featureEffect.referenceValue}
                          </span>
                        )}
                      </div>
                      {featureEffect.referencePrediction != null && (
                        <span className="small text-muted">
                          Baseline Output: <strong>{featureEffect.referencePrediction}</strong>
                          {featureEffect.referenceClass ? ` (${featureEffect.referenceClass})` : ''}
                        </span>
                      )}
                    </div>

                    <div style={{ height: '300px' }} data-testid="benchmark-feature-chart-container">
                      {featureEffectChartData?.type === 'line' ? (
                        <Line data={featureEffectChartData.data} options={featureEffectChartData.options} />
                      ) : featureEffectChartData?.type === 'bar' ? (
                        <Bar data={featureEffectChartData.data} options={featureEffectChartData.options} />
                      ) : (
                        <div className="text-muted small text-center py-4">No chart data available for this feature.</div>
                      )}
                    </div>

                    <p className="small text-muted mt-3 mb-0">
                      <i className="bi bi-info-circle me-1"></i>
                      {featureEffect.scientificNote}
                    </p>
                  </div>
                )}
              </div>
            </div>

            {/* Interactive Counterfactual What-If Simulator */}
            <div className="card shadow-sm border-0 mb-4">
              <div className="card-header bg-white py-3 border-0">
                <h5 className="fw-bold mb-0">
                  <i className="bi bi-sliders text-primary me-2"></i>
                  Counterfactual "What-If" Scenario Simulator
                </h5>
                <small className="text-muted">
                  Simulate outcome predictions dynamically using the trained best model.
                </small>
              </div>
              <div className="card-body p-4">
                {whatIfSchemaError ? (
                  <div className="alert alert-warning d-flex align-items-center mb-0">
                    <i className="bi bi-exclamation-triangle me-2"></i>
                    <span className="small">{whatIfSchemaError}</span>
                  </div>
                ) : !whatIfSchema || !whatIfSchema.length ? (
                  <div className="p-4 text-center bg-light rounded border">
                    <i className="bi bi-hourglass-split text-muted fs-3 d-block mb-2"></i>
                    <p className="text-muted small mb-0">
                      The trained model input schema is loading. Run AutoML training to enable the simulator.
                    </p>
                  </div>
                ) : (
                  <form onSubmit={handleWhatIfSubmit}>
                    <p className="small text-muted">
                      Fields show the raw dataset values. Numeric inputs use the training median and categorical inputs
                      the training mode as reference values, so any field you leave untouched keeps the reference profile.
                    </p>
                    <div className="row g-3">
                      {whatIfSchema.map((f) => (
                        <div key={f.name} className="col-md-4">
                          <label className="form-label small fw-semibold text-truncate d-block" htmlFor={`whatif-${f.name}`}>
                            {f.name}
                            <span className="text-muted fw-normal"> · {f.kind === 'NUMERIC' ? 'numeric' : 'category'}</span>
                          </label>
                          {f.kind === 'NUMERIC' ? (
                            <input
                              id={`whatif-${f.name}`}
                              type="number"
                              step="any"
                              className="form-control"
                              min={f.min ?? undefined}
                              max={f.max ?? undefined}
                              value={whatIfInputs[f.name] ?? ''}
                              onChange={(e) =>
                                setWhatIfInputs({
                                  ...whatIfInputs,
                                  [f.name]: e.target.value === '' ? '' : Number(e.target.value),
                                })
                              }
                            />
                          ) : (
                            <select
                              id={`whatif-${f.name}`}
                              className="form-select"
                              value={whatIfInputs[f.name] ?? ''}
                              onChange={(e) =>
                                setWhatIfInputs({
                                  ...whatIfInputs,
                                  [f.name]: e.target.value,
                                })
                              }
                            >
                              {(f.categories || []).map((c) => (
                                <option key={c} value={c}>
                                  {c}
                                </option>
                              ))}
                            </select>
                          )}
                          <small className="text-muted">
                            {f.kind === 'NUMERIC'
                              ? `Reference ${f.referenceValue}${
                                  f.min != null && f.max != null ? ` · training range ${f.min}–${f.max}` : ''
                                }`
                              : `Reference ${f.referenceValue}`}
                          </small>
                        </div>
                      ))}
                    </div>

                    <div className="mt-4 d-flex justify-content-end">
                      <button type="submit" className="btn btn-outline-primary fw-semibold" disabled={whatIfLoading}>
                        {whatIfLoading ? 'Computing Simulation...' : 'Simulate Counterfactual Prediction'}
                      </button>
                    </div>
                  </form>
                )}

                {whatIfResult && (
                  <div className="mt-4 p-4 bg-light rounded border">
                    <div className="row align-items-center">
                      <div className="col-md-4 text-center border-end">
                        <span className="text-muted d-block small mb-1">Simulated Prediction</span>
                        <h3 className="fw-bold text-primary mb-0">
                          {whatIfResult.problemType === 'REGRESSION'
                            ? whatIfResult.predictedValue?.toFixed(3)
                            : whatIfResult.predictedClass}
                        </h3>
                      </div>
                      <div className="col-md-4 text-center border-end">
                        <span className="text-muted d-block small mb-1">Reference Profile Prediction</span>
                        <h4 className="fw-semibold text-secondary mb-0">
                          {whatIfResult.problemType === 'REGRESSION'
                            ? whatIfResult.referencePrediction?.toFixed(3)
                            : whatIfResult.referencePrediction?.toFixed(3)}
                        </h4>
                      </div>
                      <div className="col-md-4 text-center">
                        <span className="text-muted d-block small mb-1">Shift vs Reference Profile</span>
                        <h4
                          className={`fw-bold mb-0 ${
                            whatIfResult.deltaFromBaseline >= 0 ? 'text-success' : 'text-danger'
                          }`}
                        >
                          {whatIfResult.deltaFromBaseline >= 0 ? '+' : ''}
                          {whatIfResult.deltaFromBaseline?.toFixed(3)}
                        </h4>
                      </div>
                    </div>

                    <p className="small text-muted mb-0 mt-2">
                      {whatIfResult.referenceDescription}
                      {whatIfResult.baselineMean != null
                        ? ` Training target mean: ${Number(whatIfResult.baselineMean).toFixed(3)}.`
                        : ''}
                    </p>

                    {whatIfResult.warnings?.length > 0 && (
                      <div className="alert alert-warning mt-3 mb-0">
                        <small className="fw-semibold d-block mb-1">Input adjustments</small>
                        <ul className="small mb-0 ps-3">
                          {whatIfResult.warnings.map((w, i) => (
                            <li key={i}>{w}</li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {whatIfResult.unrecognisedInputs?.length > 0 && (
                      <div className="alert alert-info mt-3 mb-0">
                        <small className="fw-semibold d-block mb-1">Ignored inputs</small>
                        <small>
                          The model was not trained on{' '}
                          {whatIfResult.unrecognisedInputs.join(', ')} — these values were not used.
                        </small>
                      </div>
                    )}

                    {whatIfResult.contributions?.length > 0 && (
                      <div className="mt-4">
                        <h6 className="fw-bold mb-1">Feature contributions</h6>
                        <small className="text-muted d-block mb-2">
                          {whatIfResult.contributionMethod} —{' '}
                          {whatIfResult.contributionsExact
                            ? 'exact for this model family.'
                            : 'approximate for this model family (tree ensembles are not additive).'}
                        </small>
                        <div className="table-responsive">
                          <table className="table table-sm align-middle mb-0">
                            <thead className="table-light">
                              <tr>
                                <th scope="col">Feature</th>
                                <th scope="col">Scenario value</th>
                                <th scope="col">Reference value</th>
                                <th scope="col">Prediction without change</th>
                                <th scope="col">Contribution</th>
                              </tr>
                            </thead>
                            <tbody>
                              {whatIfResult.contributions.map((c) => (
                                <tr key={c.featureName}>
                                  <td className="fw-semibold">{c.featureName}</td>
                                  <td>{String(c.scenarioValue)}</td>
                                  <td className="text-muted">{String(c.referenceValue)}</td>
                                  <td>{c.referencePrediction?.toFixed(3)}</td>
                                  <td
                                    className={`fw-semibold ${
                                      c.delta > 0 ? 'text-success' : c.delta < 0 ? 'text-danger' : 'text-muted'
                                    }`}
                                  >
                                    {c.delta > 0 ? '+' : ''}
                                    {c.delta?.toFixed(3)}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}

                    {whatIfResult.keyInfluences?.length > 0 && (
                      <div className="mt-3">
                        <small className="text-muted d-block mb-1">Global drivers (permutation importance)</small>
                        <div className="d-flex flex-wrap gap-2">
                          {whatIfResult.keyInfluences.map((inf) => (
                            <span key={inf} className="badge bg-light text-dark border">
                              {inf}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </WorkflowNavigator>
    </div>
  );
}
