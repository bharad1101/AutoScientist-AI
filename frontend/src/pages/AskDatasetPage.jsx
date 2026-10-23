import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useWorkflow } from '../context/WorkflowContext';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';
import PrerequisiteAlert from '../components/PrerequisiteAlert';
import { Bar, Line } from 'react-chartjs-2';

export default function AskDatasetPage() {
  const { id } = useParams();
  const { datasetId: activeDatasetId, selectDataset } = useWorkflow();
  const targetId = id || activeDatasetId;

  const [datasets, setDatasets] = useState([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState(targetId || '');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [error, setError] = useState(null);

  const sampleQueries = [
    'What is the average value of each numeric feature?',
    'How is the overall data quality and missingness?',
    'Which variables have the strongest correlation or association?',
    'What is the best performing machine learning model?',
    'Summarize this dataset and its structure',
  ];

  useEffect(() => {
    loadDatasets();
  }, [id, activeDatasetId]);

  const loadDatasets = async () => {
    try {
      const res = await datasetService.getDatasets();
      if (res.data && res.data.length > 0) {
        setDatasets(res.data);
        const resolvedId = targetId || res.data[0].id;
        setSelectedDatasetId(resolvedId);
        if (targetId && targetId !== activeDatasetId) {
          selectDataset(targetId);
        }
      }
    } catch (err) {
      if (targetId) {
        try {
          const single = await datasetService.getDataset(targetId);
          if (single.data) {
            setDatasets([single.data]);
            setSelectedDatasetId(targetId);
            return;
          }
        } catch (_) {}
      }
      setError('Failed to load datasets.');
    }
  };

  const handleAsk = async (queryText) => {
    const textToAsk = queryText || query;
    if (!textToAsk || !selectedDatasetId) return;

    setLoading(true);
    setError(null);
    try {
      const res = await analysisService.askDataset(selectedDatasetId, textToAsk);
      setResponse(res.data);
      setQuery(textToAsk);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to process dataset query.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container-fluid py-4 px-4">
      <PrerequisiteAlert stageKey="overview" />

      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="fw-bold mb-1">
            <i className="bi bi-chat-left-dots-fill text-primary me-2"></i>
            Ask Dataset — Natural Language Query Engine
          </h2>
          <p className="text-muted mb-0">
            Query your dataset in natural language with zero fabrication — backed by exact mathematical computations.
          </p>
        </div>
      </div>

      {error && (
        <div className="alert alert-danger alert-dismissible fade show" role="alert">
          <i className="bi bi-exclamation-triangle-fill me-2"></i>
          {error}
          <button type="button" className="btn-close" onClick={() => setError(null)}></button>
        </div>
      )}

      {/* Query Bar Card */}
      <div className="card shadow-sm border-0 mb-4">
        <div className="card-body p-4">
          <div className="row g-3 align-items-center mb-3">
            <div className="col-md-4">
              <label className="form-label fw-semibold">Target Dataset</label>
              <select
                className="form-select"
                value={selectedDatasetId}
                onChange={(e) => setSelectedDatasetId(e.target.value)}
                disabled={loading}
              >
                {datasets.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name} ({d.rowCount} rows)
                  </option>
                ))}
              </select>
            </div>

            <div className="col-md-8">
              <label className="form-label fw-semibold">Ask a Question</label>
              <div className="input-group">
                <input
                  type="text"
                  className="form-control"
                  placeholder="e.g. What is the average study_hours? Or which variables correlate most?"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleAsk(query)}
                  disabled={loading}
                />
                <button
                  className="btn btn-primary px-4 fw-semibold"
                  type="button"
                  onClick={() => handleAsk(query)}
                  disabled={loading || !query.trim()}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                      Computing...
                    </>
                  ) : (
                    <>
                      <i className="bi bi-send-fill me-2"></i>
                      Ask
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>

          {/* Quick Prompt Chips */}
          <div>
            <small className="text-muted fw-semibold me-2">Suggested Inquiries:</small>
            <div className="d-flex flex-wrap gap-2 mt-2">
              {sampleQueries.map((q, idx) => (
                <button
                  key={idx}
                  type="button"
                  className="btn btn-sm btn-outline-secondary rounded-pill"
                  onClick={() => handleAsk(q)}
                  disabled={loading}
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Answer View */}
      {response && (
        <div className="card shadow-sm border-0 mb-4">
          <div className="card-header bg-white py-3 border-0 d-flex justify-content-between align-items-center">
            <div className="d-flex align-items-center gap-2">
              <span className="badge bg-primary px-3 py-2">
                Intent: {response.detectedIntent}
              </span>
              <span className="badge bg-light text-dark border">
                Confidence: {((response.confidence ?? 0) * 100).toFixed(0)}%
              </span>
            </div>
            {response.relevantColumns && response.relevantColumns.length > 0 && (
              <small className="text-muted">
                Referenced columns: {response.relevantColumns.join(', ')}
              </small>
            )}
          </div>
          <div className="card-body p-4">
            <div className="bg-light p-4 rounded mb-4" style={{ whiteSpace: 'pre-line' }}>
              {response.answerMarkdown}
            </div>

            {response.chart && Array.isArray(response.chart.labels) && response.chart.labels.length > 0 && (
              <div className="mb-4">
                <h6 className="fw-bold mb-3 text-secondary">
                  <i className="bi bi-bar-chart-line me-2"></i>
                  {response.chart.title || 'Computed chart'}
                </h6>
                <div className="border rounded bg-white p-3" style={{ height: '320px' }}>
                  {response.chart.type === 'line' ? (
                    <Line
                      data={{
                        labels: response.chart.labels,
                        datasets: [
                          {
                            label: response.chart.title || 'Value',
                            data: response.chart.values,
                            borderColor: 'rgb(37, 99, 235)',
                            backgroundColor: 'rgba(37, 99, 235, 0.15)',
                            fill: true,
                            tension: 0.25,
                            pointRadius: response.chart.labels.length > 80 ? 0 : 2,
                          },
                        ],
                      }}
                      options={{
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } },
                        scales: {
                          x: { title: { display: true, text: response.chart.xLabel || '' } },
                          y: { title: { display: true, text: response.chart.yLabel || '' } },
                        },
                      }}
                    />
                  ) : (
                    <Bar
                      data={{
                        labels: response.chart.labels,
                        datasets: [
                          {
                            label: response.chart.title || 'Value',
                            data: response.chart.values,
                            backgroundColor: 'rgba(37, 99, 235, 0.7)',
                            borderColor: 'rgba(37, 99, 235, 1)',
                            borderWidth: 1,
                            borderRadius: 3,
                          },
                        ],
                      }}
                      options={{
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } },
                        scales: {
                          x: { title: { display: true, text: response.chart.xLabel || '' } },
                          y: { title: { display: true, text: response.chart.yLabel || '' } },
                        },
                      }}
                    />
                  )}
                </div>
              </div>
            )}

            {Array.isArray(response.citations) && response.citations.length > 0 && (
              <div className="mb-3">
                <h6 className="fw-bold mb-2 text-secondary">
                  <i className="bi bi-bookmark-check me-2"></i>
                  Evidence Citations
                </h6>
                <ul className="small text-muted mb-0 ps-3">
                  {response.citations.map((c, i) => (
                    <li key={i}>{c}</li>
                  ))}
                </ul>
              </div>
            )}

            {response.empiricalEvidence && Object.keys(response.empiricalEvidence).length > 0 && (
              <div>
                <h6 className="fw-bold mb-3 text-secondary">
                  <i className="bi bi-graph-up me-2"></i>
                  Extracted Mathematical Evidence
                </h6>
                <div className="row g-2">
                  {Object.entries(response.empiricalEvidence).map(([key, val]) => (
                    <div key={key} className="col-auto">
                      <div className="badge bg-white text-dark border p-2 fw-normal">
                        <strong className="text-primary">{key}:</strong>{' '}
                        {typeof val === 'number' ? val.toFixed(3) : String(val)}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
