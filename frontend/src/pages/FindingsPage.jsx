import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { datasetService } from '../services/datasetService';
import { researchService } from '../services/researchService';
import { analysisService } from '../services/analysisService';
import EvidenceGraphPanel, { ReliabilityScorecard } from '../components/EvidenceGraphPanel';
import WorkflowNavigator from '../components/WorkflowNavigator';
import { useWorkflow } from '../context/WorkflowContext';

const FindingsPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { datasetId: workflowDatasetId, selectDataset } = useWorkflow();
  // The dataset ID in the route is authoritative; the workflow-context ID is
  // only a fallback for the legacy "/findings" route that has no :id param.
  const activeId = id || workflowDatasetId || '';

  const [datasets, setDatasets] = useState([]);
  const [dataset, setDataset] = useState(null);
  const [findings, setFindings] = useState([]);
  const [evidence, setEvidence] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // Monotonic token so a slow response for a previously selected dataset can
  // never overwrite the state of the currently selected one.
  const requestSeqRef = useRef(0);

  const loadData = useCallback(async (targetId) => {
    const seq = ++requestSeqRef.current;

    if (!targetId) {
      setDataset(null);
      setFindings([]);
      setEvidence(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    // Drop the previous dataset's data immediately so it is never rendered
    // against the dataset that is currently loading.
    setDataset(null);
    setFindings([]);
    setEvidence(null);
    if (selectDataset) selectDataset(targetId);

    const [dsSettled, fSettled, evSettled] = await Promise.allSettled([
      datasetService.getDataset(targetId),
      researchService.getDatasetFindings(targetId),
      analysisService.getEvidenceGraph(targetId),
    ]);

    // A newer request has been issued since: discard this stale response.
    if (seq !== requestSeqRef.current) return;

    if (fSettled.status === 'rejected') {
      const err = fSettled.reason;
      console.error('Failed to load findings:', err);
      setError(err?.response?.data?.message || err?.message || 'Failed to load scientific findings.');
      setFindings([]);
    } else {
      const body = fSettled.value;
      const list = Array.isArray(body) ? body : Array.isArray(body?.data) ? body.data : [];
      setFindings(list);
    }

    if (dsSettled.status === 'fulfilled' && dsSettled.value && dsSettled.value.data) {
      setDataset(dsSettled.value.data);
    }

    setEvidence(
      evSettled.status === 'fulfilled' && evSettled.value && evSettled.value.data
        ? evSettled.value.data
        : null
    );
    setLoading(false);
  }, [selectDataset]);

  useEffect(() => {
    if (activeId) {
      loadData(activeId);
    } else {
      datasetService
        .listDatasets()
        .then((res) => {
          const list = Array.isArray(res?.data) ? res.data : [];
          if (list.length > 0) {
            // Normalize the legacy no-id route to the canonical URL so the
            // route ID becomes authoritative from here on.
            navigate(`/datasets/${list[0].id}/findings`, { replace: true });
          } else {
            setLoading(false);
          }
        })
        .catch((err) => {
          console.error('Failed to list datasets:', err);
          setError('Failed to load scientific findings.');
          setLoading(false);
        });
    }

    datasetService
      .listDatasets()
      .then((res) => {
        if (Array.isArray(res?.data)) setDatasets(res.data);
      })
      .catch(() => {});
  }, [activeId, loadData, navigate]);

  // Invalidate any in-flight request when the page unmounts.
  useEffect(() => {
    return () => {
      requestSeqRef.current += 1;
    };
  }, []);

  const handleDatasetChange = (dsId) => {
    navigate(`/datasets/${dsId}/findings`);
  };

  const getConfidenceBadge = (level) => {
    switch (level) {
      case 'HIGH':
        return <span className="badge bg-success">High Confidence</span>;
      case 'MODERATE':
        return <span className="badge bg-primary">Moderate Confidence</span>;
      case 'WEAK':
        return <span className="badge bg-warning text-dark">Weak Confidence</span>;
      default:
        return <span className="badge bg-secondary">Insufficient Evidence</span>;
    }
  };

  return (
    <div className="container py-4 w-100 min-w-0">
      <WorkflowNavigator currentStage="findings">
        <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
          <div>
            <h2 className="fw-bold mb-1">
              <i className="bi bi-award-fill text-warning me-2"></i>Scientific Findings & Evidence Repository
            </h2>
            <p className="text-muted small mb-0">
              Validated empirical discoveries linked to dataset versions and statistical provenance nodes.
            </p>
          </div>

          <div className="d-flex align-items-center gap-2">
            {dataset && (
              <span className="badge bg-light text-dark border p-2">
                <i className="bi bi-database me-1 text-primary"></i>
                <strong>{dataset.name}</strong> ({dataset.rowCount} rows)
              </span>
            )}

            {datasets.length > 1 && (
              <select
                className="form-select form-select-sm"
                value={activeId}
                onChange={(e) => handleDatasetChange(e.target.value)}
                style={{ width: '180px' }}
                aria-label="Select dataset"
              >
                {datasets.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            )}
          </div>
        </div>

        {error && (
          <div className="alert alert-danger shadow-sm d-flex justify-content-between align-items-center mb-4" role="alert">
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

        {loading ? (
          <div className="text-center py-5">
            <div className="spinner-border text-primary" role="status"></div>
            <p className="text-muted small mt-2">Loading evidence trail...</p>
          </div>
        ) : (
          <>
            {evidence && (
              <>
                <ReliabilityScorecard reliability={evidence.reliability} />
                <EvidenceGraphPanel graph={evidence} />
              </>
            )}
          </>
        )}

        {/* Empty state is only shown when the load succeeded and the API
            genuinely returned zero findings — never while loading or after an
            error, so a failed request cannot masquerade as "no findings". */}
        {!loading && !error && findings.length === 0 && (
          <div className="card text-center py-5 border-dashed bg-white shadow-sm">
            <div className="card-body">
              <i className="bi bi-journal-x display-4 text-muted mb-2 d-block"></i>
              <h5 className="fw-bold">No Evidence-Backed Findings Generated Yet</h5>
              <p className="text-muted small max-w-md mx-auto mb-3">
                Findings are synthesized from statistical hypothesis testing decisions or predictive models.
              </p>
              {activeId && (
                <Link to={`/datasets/${activeId}/research`} className="btn btn-primary fw-semibold px-4 py-2">
                  <i className="bi bi-question-circle me-1"></i> Formulate Research Question
                </Link>
              )}
            </div>
          </div>
        )}

        {!loading && findings.length > 0 && (
          <div className="row g-4">
            {findings.map((f) => (
              <div key={f.id} className="col-12">
                <div className="card shadow-sm border-0 p-4 bg-white">
                  <div className="d-flex justify-content-between align-items-start mb-2 flex-wrap gap-2">
                    <div>
                      <div className="d-flex align-items-center gap-2 mb-2">
                        {getConfidenceBadge(f.confidenceLevel)}
                        <span className="text-muted small">
                          Recorded on {new Date(f.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                      <h4 className="fw-bold text-dark mb-1">{f.title}</h4>
                    </div>
                  </div>

                  <p className="lead text-secondary fs-6 mb-3">{f.summary}</p>

                  <div className="bg-light p-3 rounded border mb-3">
                    <h6 className="fw-bold mb-2 small text-muted">EMPIRICAL STATISTICAL METRICS</h6>
                    <pre className="mb-0 small text-dark font-monospace" style={{ whiteSpace: 'pre-wrap' }}>
                      {f.evidenceText}
                    </pre>
                  </div>

                  {/* Evidence Nodes Provenance Trail */}
                  {f.evidenceNodes && f.evidenceNodes.length > 0 && (
                    <>
                      <h6 className="fw-bold mb-2 small text-muted">PROVENANCE GRAPH VERTICES</h6>
                      <div className="row g-2 mb-3">
                        {f.evidenceNodes.map((node) => (
                          <div key={node.id} className="col-md-4">
                            <div className="p-2 border rounded bg-white small h-100 shadow-sm">
                              <span className="badge bg-dark mb-1">{node.nodeType}</span>
                              <div className="text-secondary small">{node.description}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </>
                  )}

                  {/* Causality disclaimer */}
                  <div className="alert alert-light border small text-muted mb-0 py-2 px-3">
                    <i className="bi bi-info-circle me-1"></i>
                    <strong>Disclaimer: </strong>{f.causalityDisclaimer || 'Observational finding only; confounding unaddressed.'}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </WorkflowNavigator>
    </div>
  );
};

export default FindingsPage;
