import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Bar, Line, Scatter } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  BarController,
  LineController,
  ScatterController,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import { analysisService } from '../services/analysisService';
import { numericAnalyticColumns, categoricalAnalyticColumns, isInAnalysis } from '../utils/columns';

ChartJS.register(
  BarController,
  LineController,
  ScatterController,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

const CHART_COLORS = [
  'rgba(37, 99, 235, 0.75)',
  'rgba(14, 165, 233, 0.75)',
  'rgba(16, 185, 129, 0.75)',
  'rgba(245, 158, 11, 0.75)',
  'rgba(239, 68, 68, 0.75)',
  'rgba(139, 92, 246, 0.75)',
];

const palette = (count) => Array.from({ length: count }, (_, i) => CHART_COLORS[i % CHART_COLORS.length]);

function CardShell({ icon, title, subtitle, children, actions }) {
  return (
    <div className="card shadow-sm border-0 mb-4">
      <div className="card-header bg-white py-3 border-bottom d-flex flex-wrap justify-content-between align-items-center gap-2">
        <div>
          <h5 className="fw-bold mb-0">
            <i className={`bi ${icon} text-primary me-2`}></i>
            {title}
          </h5>
          <small className="text-muted">{subtitle}</small>
        </div>
        {actions}
      </div>
      <div className="card-body p-4">{children}</div>
    </div>
  );
}

function EmptyState({ icon, message }) {
  return (
    <div className="p-4 text-center bg-light rounded border">
      <i className={`bi ${icon} text-muted fs-3 d-block mb-2`}></i>
      <p className="text-muted small mb-0">{message}</p>
    </div>
  );
}

export default function EdaGallery({ datasetId, columns = [] }) {
  const numCols = useMemo(() => numericAnalyticColumns(columns), [columns]);
  const catCols = useMemo(() => categoricalAnalyticColumns(columns), [columns]);
  const timeCols = useMemo(() => columns.filter((c) => isInAnalysis(c)), [columns]);

  const [histVar, setHistVar] = useState('');
  const [histograms, setHistograms] = useState([]);
  const [histLoading, setHistLoading] = useState(false);
  const [histError, setHistError] = useState('');

  const [scatterX, setScatterX] = useState('');
  const [scatterY, setScatterY] = useState('');
  const [scatter, setScatter] = useState(null);
  const [scatterLoading, setScatterLoading] = useState(false);
  const [scatterError, setScatterError] = useState('');

  const [missingness, setMissingness] = useState([]);
  const [missingLoading, setMissingLoading] = useState(false);
  const [missingError, setMissingError] = useState('');

  const [catVar, setCatVar] = useState('');
  const [categorical, setCategorical] = useState([]);
  const [catLoading, setCatLoading] = useState(false);
  const [catError, setCatError] = useState('');

  const [timeVar, setTimeVar] = useState('');
  const [valueVar, setValueVar] = useState('');
  const [aggregation, setAggregation] = useState(0);
  const [series, setSeries] = useState([]);
  const [seriesLoading, setSeriesLoading] = useState(false);
  const [seriesError, setSeriesError] = useState('');

  const [densityNumVar, setDensityNumVar] = useState('');
  const [densityCatVar, setDensityCatVar] = useState('');
  const [groupDistributions, setGroupDistributions] = useState([]);
  const [densityLoading, setDensityLoading] = useState(false);
  const [densityError, setDensityError] = useState('');

  const [missingMatrix, setMissingMatrix] = useState(null);
  const [matrixLoading, setMatrixLoading] = useState(false);
  const [matrixError, setMatrixError] = useState('');

  useEffect(() => {
    setHistVar(numCols[0]?.columnName || '');
    setScatterX(numCols[0]?.columnName || '');
    setScatterY(numCols[1]?.columnName || '');
    const dateCol = columns.find((c) => c.dataType === 'DATE_TIME' && isInAnalysis(c));
    setTimeVar(dateCol?.columnName || '');
    setValueVar(numCols[0]?.columnName || '');
    setAggregation(0);
    setCatVar(catCols[0]?.columnName || '');
    setDensityNumVar(numCols[0]?.columnName || '');
    setDensityCatVar(catCols[0]?.columnName || '');
  }, [datasetId, columns, numCols, catCols]);

  const safeCall = useCallback(async (fn, setter, setLoading, setErr, label) => {
    if (!datasetId) return;
    try {
      setLoading(true);
      setErr('');
      const res = await fn();
      setter(res?.data ?? []);
    } catch (err) {
      setErr(err.response?.data?.message || `Unable to compute ${label}.`);
    } finally {
      setLoading(false);
    }
  }, [datasetId]);

  useEffect(() => {
    let active = true;
    if (!datasetId || numCols.length === 0) {
      setHistograms([]);
      return;
    }
    (async () => {
      try {
        setHistLoading(true);
        setHistError('');
        const res = await analysisService.getHistograms(datasetId);
        if (active) setHistograms(res?.data ?? []);
      } catch (err) {
        if (active) setHistError(err.response?.data?.message || 'Unable to compute histograms.');
      } finally {
        if (active) setHistLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [datasetId, numCols.length]);

  useEffect(() => {
    let active = true;
    if (!datasetId) {
      setMissingness([]);
      return;
    }
    (async () => {
      try {
        setMissingLoading(true);
        setMissingError('');
        const res = await analysisService.getMissingness(datasetId);
        if (active) setMissingness(res?.data ?? []);
      } catch (err) {
        if (active) setMissingError(err.response?.data?.message || 'Unable to compute missingness.');
      } finally {
        if (active) setMissingLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [datasetId]);

  useEffect(() => {
    let active = true;
    if (!datasetId || catCols.length === 0) {
      setCategorical([]);
      return;
    }
    (async () => {
      try {
        setCatLoading(true);
        setCatError('');
        const res = await analysisService.getCategoricalSummaries(datasetId);
        if (active) setCategorical(res?.data ?? []);
      } catch (err) {
        if (active) setCatError(err.response?.data?.message || 'Unable to compute categorical summaries.');
      } finally {
        if (active) setCatLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [datasetId, catCols.length]);

  useEffect(() => {
    let active = true;
    if (!datasetId || !scatterX || !scatterY || scatterX === scatterY) {
      setScatter(null);
      return;
    }
    (async () => {
      try {
        setScatterLoading(true);
        setScatterError('');
        const res = await analysisService.getScatter(datasetId, scatterX, scatterY);
        if (active) setScatter(res?.data ?? null);
      } catch (err) {
        if (active) {
          setScatter(null);
          setScatterError(err.response?.data?.message || 'Unable to compute scatter plot.');
        }
      } finally {
        if (active) setScatterLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [datasetId, scatterX, scatterY]);

  useEffect(() => {
    let active = true;
    if (!datasetId || !timeVar || !valueVar) {
      setSeries([]);
      return;
    }
    (async () => {
      try {
        setSeriesLoading(true);
        setSeriesError('');
        const res = await analysisService.getTimeSeries(datasetId, timeVar, valueVar, aggregation);
        if (active) setSeries(res?.data ?? []);
      } catch (err) {
        if (active) {
          setSeries([]);
          setSeriesError(err.response?.data?.message || 'Unable to compute time series.');
        }
      } finally {
        if (active) setSeriesLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [datasetId, timeVar, valueVar, aggregation]);

  useEffect(() => {
    let active = true;
    if (!datasetId || !densityNumVar || !densityCatVar) {
      setGroupDistributions([]);
      return;
    }
    (async () => {
      try {
        setDensityLoading(true);
        setDensityError('');
        const res = await analysisService.getGroupDistributions(datasetId, densityNumVar, densityCatVar);
        if (active) setGroupDistributions(res?.data ?? []);
      } catch (err) {
        if (active) {
          setGroupDistributions([]);
          setDensityError(err.response?.data?.message || 'Unable to compute cohort distributions.');
        }
      } finally {
        if (active) setDensityLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [datasetId, densityNumVar, densityCatVar]);

  useEffect(() => {
    let active = true;
    if (!datasetId) {
      setMissingMatrix(null);
      return;
    }
    (async () => {
      try {
        setMatrixLoading(true);
        setMatrixError('');
        const res = await analysisService.getMissingnessMatrix(datasetId, 200);
        if (active) setMissingMatrix(res?.data ?? null);
      } catch (err) {
        if (active) {
          setMissingMatrix(null);
          setMatrixError(err.response?.data?.message || 'Unable to compute the missing-value matrix.');
        }
      } finally {
        if (active) setMatrixLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [datasetId]);

  const activeHistogram = histograms.find((h) => h.variable === histVar) || histograms[0];
  const activeCategorical = categorical.find((c) => c.variable === catVar) || categorical[0];

  const histData = activeHistogram
    ? {
        labels: activeHistogram.binStarts.map((start, i) => `${start}–${activeHistogram.binEnds[i]}`),
        datasets: [
          {
            label: `Count (${activeHistogram.variable})`,
            data: activeHistogram.counts,
            backgroundColor: 'rgba(37, 99, 235, 0.7)',
            borderColor: 'rgba(37, 99, 235, 1)',
            borderWidth: 1,
            borderRadius: 3,
          },
        ],
      }
    : null;

  const validScatterPoints = useMemo(() => {
    return (scatter?.points || []).filter(
      (p) => Array.isArray(p) && p.length >= 2 && Number.isFinite(p[0]) && Number.isFinite(p[1])
    );
  }, [scatter]);

  const scatterData = scatter
    ? {
        datasets: [
          {
            type: 'scatter',
            label: `${scatter.xVariable} vs ${scatter.yVariable}`,
            data: validScatterPoints.map((p) => ({ x: p[0], y: p[1] })),
            backgroundColor: 'rgba(14, 165, 233, 0.6)',
            pointRadius: 3,
            order: 2,
          },
          ...(scatter.fit && Number.isFinite(scatter.fit.slope) && Number.isFinite(scatter.fit.intercept)
            ? (() => {
                const xs = validScatterPoints.map((p) => p[0]);
                if (!xs.length) return [];
                const minX = Math.min(...xs);
                const maxX = Math.max(...xs);
                const { slope, intercept } = scatter.fit;
                return [
                  {
                    type: 'line',
                    label: 'OLS fit',
                    data: [
                      { x: minX, y: slope * minX + intercept },
                      { x: maxX, y: slope * maxX + intercept },
                    ],
                    borderColor: 'rgba(239, 68, 68, 0.95)',
                    backgroundColor: 'rgba(239, 68, 68, 0.95)',
                    borderWidth: 2,
                    pointRadius: 0,
                    fill: false,
                    tension: 0,
                    order: 1,
                  },
                ];
              })()
            : []),
        ],
      }
    : null;

  const missingData = missingness.length
    ? {
        labels: missingness.map((m) => m.variable),
        datasets: [
          {
            label: 'Missing %',
            data: missingness.map((m) => m.missingPct),
            backgroundColor: missingness.map((m) =>
              m.missingPct > 20 ? 'rgba(239, 68, 68, 0.75)' : m.missingPct > 5 ? 'rgba(245, 158, 11, 0.75)' : 'rgba(16, 185, 129, 0.75)'
            ),
            borderRadius: 3,
          },
        ],
      }
    : null;

  const categoricalData = activeCategorical
    ? {
        labels: (activeCategorical.topCategories || []).map((c) => c.category),
        datasets: [
          {
            label: `Count (${activeCategorical.variable})`,
            data: (activeCategorical.topCategories || []).map((c) => c.count),
            backgroundColor: palette((activeCategorical.topCategories || []).length),
            borderRadius: 3,
          },
        ],
      }
    : null;

  const seriesData = series.length
    ? {
        labels: series.map((p) => p.timestamp),
        datasets: [
          {
            label: `${valueVar} by ${timeVar}`,
            data: series.map((p) => p.value),
            borderColor: 'rgba(37, 99, 235, 1)',
            backgroundColor: 'rgba(37, 99, 235, 0.15)',
            fill: true,
            tension: 0.25,
            pointRadius: series.length > 120 ? 0 : 2,
          },
        ],
      }
    : null;

  const densityData = groupDistributions.length
    ? {
        labels: (() => {
          const first = groupDistributions[0];
          return (first.binStarts || []).map((start, i) => {
            const end = first.binEnds?.[i];
            return `${start}–${end}`;
          });
        })(),
        datasets: groupDistributions.map((g, i) => ({
          label: `${g.groupName} (n=${g.count})`,
          data: (g.proportions || []).map((p) => p * 100),
          borderColor: CHART_COLORS[i % CHART_COLORS.length],
          backgroundColor: 'transparent',
          borderWidth: 2,
          pointRadius: 0,
          tension: 0.3,
          fill: false,
        })),
      }
    : null;

  const matrixVariables = useMemo(() => {
    const vars = missingMatrix?.variables || [];
    return vars.slice(0, 40);
  }, [missingMatrix]);

  const matrixRows = useMemo(() => {
    if (!missingMatrix?.present) return [];
    return missingMatrix.present.map((row, idx) => ({
      rowIndex: missingMatrix.rowIndexes?.[idx] ?? idx,
      cells: matrixVariables.map((_, c) => Boolean(row[c])),
    }));
  }, [missingMatrix, matrixVariables]);

  const selectClass = 'form-select form-select-sm w-auto';
  const chartHeight = { height: '320px' };

  return (
    <div>
      <CardShell
        icon="bi-bar-chart-line"
        title="Numeric Distributions"
        subtitle="Empirical histograms computed from every numeric, non-identifier column (up to 20 bins)."
        actions={
          numCols.length > 0 && (
            <select className={selectClass} value={histVar} onChange={(e) => setHistVar(e.target.value)}>
              {numCols.map((c) => (
                <option key={c.columnName} value={c.columnName}>{c.columnName}</option>
              ))}
            </select>
          )
        }
      >
        {histLoading ? (
          <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary"></div></div>
        ) : histError ? (
          <EmptyState icon="bi-exclamation-triangle" message={histError} />
        ) : histData ? (
          <div style={chartHeight}>
            <Bar
              data={histData}
              options={{
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                  x: { title: { display: true, text: activeHistogram?.variable } },
                  y: { title: { display: true, text: 'Frequency' }, beginAtZero: true },
                },
              }}
            />
          </div>
        ) : (
          <EmptyState icon="bi-bar-chart" message="No numeric variables are available for distribution analysis." />
        )}
      </CardShell>

      <CardShell
        icon="bi-bounding-box"
        title="Bivariate Scatter"
        subtitle="Row-level relationship between two numeric variables. Large datasets are deterministically downsampled."
        actions={
          numCols.length > 1 && (
            <div className="d-flex gap-2">
              <select className={selectClass} value={scatterX} onChange={(e) => setScatterX(e.target.value)}>
                {numCols.map((c) => <option key={c.columnName} value={c.columnName}>X: {c.columnName}</option>)}
              </select>
              <select className={selectClass} value={scatterY} onChange={(e) => setScatterY(e.target.value)}>
                {numCols.map((c) => <option key={c.columnName} value={c.columnName}>Y: {c.columnName}</option>)}
              </select>
            </div>
          )
        }
      >
        {scatterLoading ? (
          <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary"></div></div>
        ) : scatterError ? (
          <EmptyState icon="bi-exclamation-triangle" message={scatterError} />
        ) : scatterData ? (
          <>
            <div style={chartHeight}>
              <Scatter
                data={scatterData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: { legend: { display: false } },
                  scales: {
                    x: { title: { display: true, text: scatter.xVariable } },
                    y: { title: { display: true, text: scatter.yVariable } },
                  },
                }}
              />
            </div>
            {scatter.downsampled && (
              <p className="small text-muted mb-0 mt-2">
                <i className="bi bi-info-circle me-1"></i>
                Showing {scatter.points?.length} of {scatter.totalRows} rows (deterministic downsample).
              </p>
            )}
            {scatter.fit && (
              <div className="row g-3 mt-1">
                <div className="col-6 col-md-3">
                  <span className="text-muted small d-block">Slope</span>
                  <span className="fw-semibold">{Number(scatter.fit.slope).toFixed(4)}</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted small d-block">Intercept</span>
                  <span className="fw-semibold">{Number(scatter.fit.intercept).toFixed(4)}</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted small d-block">R²</span>
                  <span className="fw-semibold">{Number(scatter.fit.rSquared).toFixed(4)}</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted small d-block">Complete cases (n)</span>
                  <span className="fw-semibold">{scatter.fit.n}</span>
                </div>
              </div>
            )}
            {scatter.fit && (
              <p className="small text-muted mb-0 mt-2">
                <i className="bi bi-info-circle me-1"></i>
                Ordinary-least-squares fit computed on the complete-case rows of the plotted sample.
              </p>
            )}
          </>
        ) : (
          <EmptyState icon="bi-bounding-box" message="Select two distinct numeric variables to render a scatter plot." />
        )}
      </CardShell>

      <CardShell
        icon="bi-clipboard-data"
        title="Missingness Audit"
        subtitle="Per-variable missing-value percentage across active columns."
      >        {missingLoading ? (
          <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary"></div></div>
        ) : missingError ? (
          <EmptyState icon="bi-exclamation-triangle" message={missingError} />
        ) : missingData ? (
          <div style={{ height: `${Math.max(220, missingness.length * 28)}px` }}>
            <Bar
              data={missingData}
              options={{
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                  x: { beginAtZero: true, title: { display: true, text: 'Missing (%)' } },
                },
              }}
            />
          </div>
        ) : (
          <EmptyState icon="bi-clipboard-data" message="No active columns to audit." />
        )}
      </CardShell>

      <CardShell
        icon="bi-grid-3x3-gap"
        title="Missing-Value Matrix"
        subtitle="Row-level presence map: every cell is a real observation, dark cells are missing values."
      >
        {matrixLoading ? (
          <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary"></div></div>
        ) : matrixError ? (
          <EmptyState icon="bi-exclamation-triangle" message={matrixError} />
        ) : matrixRows.length && matrixVariables.length ? (
          <>
            <div className="table-responsive" style={{ maxHeight: '420px', overflowY: 'auto' }}>
              <table className="table table-sm table-bordered mb-0" style={{ fontSize: '0.72rem' }}>
                <caption className="visually-hidden">
                  Missing-value matrix: rows are dataset rows, columns are variables. Present values are shown in
                  light grey, missing values in dark red.
                </caption>
                <thead className="sticky-top" style={{ backgroundColor: '#fff' }}>
                  <tr>
                    <th scope="col" className="text-end">Row</th>
                    {matrixVariables.map((v) => (
                      <th key={v} scope="col" className="text-nowrap">{v}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {matrixRows.map((r) => (
                    <tr key={r.rowIndex}>
                      <th scope="row" className="text-end text-muted fw-normal">{r.rowIndex}</th>
                      {r.cells.map((present, c) => (
                        <td
                          key={`${r.rowIndex}-${matrixVariables[c]}`}
                          style={{
                            backgroundColor: present ? 'rgba(37, 99, 235, 0.10)' : 'rgba(220, 38, 38, 0.85)',
                            width: '18px',
                            height: '14px',
                            padding: 0,
                          }}
                          title={`${matrixVariables[c]} — row ${r.rowIndex}: ${present ? 'present' : 'missing'}`}
                        />
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="d-flex flex-wrap gap-3 align-items-center mt-2 small text-muted">
              <span><span style={{ display: 'inline-block', width: '12px', height: '12px', background: 'rgba(37, 99, 235, 0.10)', border: '1px solid #ccc' }}></span> observed</span>
              <span><span style={{ display: 'inline-block', width: '12px', height: '12px', background: 'rgba(220, 38, 38, 0.85)' }}></span> missing</span>
              <span>
                Showing {matrixRows.length} of {missingMatrix?.totalRows} rows
                {missingMatrix?.truncated ? ' (deterministic sample of the first rows)' : ''}
                {missingMatrix?.variables?.length > matrixVariables.length
                  ? ` · first ${matrixVariables.length} of ${missingMatrix.variables.length} columns`
                  : ''}
              </span>
            </div>
          </>
        ) : (
          <EmptyState icon="bi-grid-3x3-gap" message="No active columns to map." />
        )}
      </CardShell>

      <CardShell
        icon="bi-bezier2"
        title="Cohort Distribution Comparison"
        subtitle="Density of a numeric variable per cohort, measured on shared bins so the curves are comparable."
        actions={
          numCols.length > 0 && catCols.length > 0 && (
            <div className="d-flex gap-2 flex-wrap">
              <select className={selectClass} value={densityNumVar} onChange={(e) => setDensityNumVar(e.target.value)}>
                {numCols.map((c) => <option key={c.columnName} value={c.columnName}>Value: {c.columnName}</option>)}
              </select>
              <select className={selectClass} value={densityCatVar} onChange={(e) => setDensityCatVar(e.target.value)}>
                {catCols.map((c) => <option key={c.columnName} value={c.columnName}>Cohort: {c.columnName}</option>)}
              </select>
            </div>
          )
        }
      >
        {numCols.length === 0 || catCols.length === 0 ? (
          <EmptyState icon="bi-bezier2" message="A cohort comparison needs at least one numeric and one categorical column." />
        ) : densityLoading ? (
          <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary"></div></div>
        ) : densityError ? (
          <EmptyState icon="bi-exclamation-triangle" message={densityError} />
        ) : densityData ? (
          <>
            <div style={chartHeight}>
              <Line
                data={densityData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: { legend: { position: 'bottom' } },
                  scales: {
                    x: { title: { display: true, text: densityNumVar } },
                    y: { title: { display: true, text: 'Share of cohort (%)' }, beginAtZero: true },
                  },
                }}
              />
            </div>
            <div className="row g-2 mt-1 small text-muted">
              {groupDistributions.map((g) => (
                <div className="col-6 col-md-4" key={g.groupName}>
                  <span className="fw-semibold text-dark">{g.groupName}</span> · n={g.count} · mean{' '}
                  {Number(g.mean).toFixed(2)} · median {Number(g.median).toFixed(2)} · σ {Number(g.stdDev).toFixed(2)}
                </div>
              ))}
            </div>
          </>
        ) : (
          <EmptyState icon="bi-bezier2" message="No cohort produced a usable distribution for this column pair." />
        )}
      </CardShell>

      <CardShell
        icon="bi-tags"
        title="Categorical Composition"
        subtitle="Top categories and their observed frequencies (up to 30 categories)."
        actions={
          catCols.length > 0 && (
            <select className={selectClass} value={catVar} onChange={(e) => setCatVar(e.target.value)}>
              {catCols.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
            </select>
          )
        }
      >
        {catLoading ? (
          <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary"></div></div>
        ) : catError ? (
          <EmptyState icon="bi-exclamation-triangle" message={catError} />
        ) : categoricalData ? (
          <>
            <div style={chartHeight}>
              <Bar
                data={categoricalData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: { legend: { display: false } },
                  scales: {
                    x: { title: { display: true, text: activeCategorical?.variable } },
                    y: { beginAtZero: true, title: { display: true, text: 'Count' } },
                  },
                }}
              />
            </div>
            <p className="small text-muted mb-0 mt-2">
              {activeCategorical?.uniqueCount} unique values · {activeCategorical?.missingCount} missing
            </p>
          </>
        ) : (
          <EmptyState icon="bi-tags" message="No categorical variables are available for composition analysis." />
        )}
      </CardShell>

      <CardShell
        icon="bi-activity"
        title="Time Series"
        subtitle="Numeric values aggregated into hourly/daily buckets when the time column parses as a timestamp."
        actions={
          timeCols.length > 0 && numCols.length > 0 && (
            <div className="d-flex gap-2 flex-wrap">
              <select className={selectClass} value={timeVar} onChange={(e) => setTimeVar(e.target.value)}>
                <option value="">Time column…</option>
                {timeCols.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
              </select>
              <select className={selectClass} value={valueVar} onChange={(e) => setValueVar(e.target.value)}>
                {numCols.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
              </select>
              <select className={selectClass} value={aggregation} onChange={(e) => setAggregation(Number(e.target.value))}>
                <option value={0}>Mean</option>
                <option value={1}>Median</option>
                <option value={2}>Min</option>
                <option value={3}>Max</option>
              </select>
            </div>
          )
        }
      >
        {!timeVar ? (
          <EmptyState icon="bi-activity" message="Select a time column and a numeric value column to render a series." />
        ) : seriesLoading ? (
          <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary"></div></div>
        ) : seriesError ? (
          <EmptyState icon="bi-exclamation-triangle" message={seriesError} />
        ) : seriesData ? (
          <div style={chartHeight}>
            <Line
              data={seriesData}
              options={{
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                  x: { title: { display: true, text: timeVar } },
                  y: { title: { display: true, text: valueVar }, beginAtZero: false },
                },
              }}
            />
          </div>
        ) : (
          <EmptyState icon="bi-activity" message="No plottable time/value pairs were found." />
        )}
      </CardShell>
    </div>
  );
}
