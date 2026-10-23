import React, { useState, useEffect } from 'react';
import { useParams, useSearchParams, Link, useLocation } from 'react-router-dom';
import { researchService } from '../services/researchService';
import { datasetService } from '../services/datasetService';
import WorkflowNavigator from '../components/WorkflowNavigator';
import { useWorkflow } from '../context/WorkflowContext';
import { analyticColumns } from '../utils/columns';

const ResearchWorkflowPage = () => {
  const { id } = useParams();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { datasetId: workflowDatasetId, selectDataset, refreshWorkflowStatus } = useWorkflow();
  const activeId = id || workflowDatasetId;

  let currentStage = 'research';
  if (location.pathname.includes('/hypotheses')) {
    currentStage = 'hypotheses';
  } else if (location.pathname.includes('/statistics')) {
    currentStage = 'statistics';
  }

  const [dataset, setDataset] = useState(null);
  const [questionText, setQuestionText] = useState('');
  const [predictorVar, setPredictorVar] = useState('');
  const [targetVar, setTargetVar] = useState('');

  const [existingQuestions, setExistingQuestions] = useState([]);
  const [currentRq, setCurrentRq] = useState(null);
  const [finding, setFinding] = useState(null);

  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [testingId, setTestingId] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const init = async () => {
      try {
        setLoading(true);
        setError('');
        let currentId = activeId;
        if (!currentId) {
          const allDs = await datasetService.listDatasets();
          if (allDs && allDs.data && allDs.data.length > 0) {
            currentId = allDs.data[0].id;
          } else {
            setLoading(false);
            return;
          }
        }
        if (selectDataset) selectDataset(currentId);

        const [dsRes, rqRes] = await Promise.all([
          datasetService.getDataset(currentId).catch(() => null),
          researchService.getDatasetResearchQuestions(currentId).catch(() => ({ data: [] })),
        ]);

        if (dsRes && dsRes.data) {
          setDataset(dsRes.data);

          // Check if parameters passed from associations [Investigate]
          const qVar1 = searchParams.get('var1');
          const qVar2 = searchParams.get('var2');
          if (qVar1 && qVar2) {
            setPredictorVar(qVar1);
            setTargetVar(qVar2);
            setQuestionText(`Is ${qVar1} statistically associated with ${qVar2}?`);
          } else {
            const nonIdCols = analyticColumns(dsRes.data.columns);
            if (nonIdCols.length >= 2) {
              const pCol = nonIdCols[0].columnName;
              const tCol = nonIdCols[1].columnName;
              setPredictorVar(pCol);
              setTargetVar(tCol);
              setQuestionText(`Is ${pCol} statistically associated with ${tCol}?`);
            }
          }
        }

        if (rqRes && rqRes.data) {
          setExistingQuestions(rqRes.data);
          if (rqRes.data.length > 0) {
            setCurrentRq(rqRes.data[0]);
          }
        }
      } catch (err) {
        console.error('Failed to init research workspace:', err);
        setError(err.response?.data?.message || err.message || 'Failed to initialize research workspace.');
      } finally {
        setLoading(false);
      }
    };

    init();
  }, [activeId, searchParams, selectDataset]);

  const handleFormulate = async (e) => {
    e.preventDefault();
    if (!questionText.trim()) return;

    const targetDatasetId = activeId || currentRq?.datasetId;
    if (!targetDatasetId) return;

    try {
      setAnalyzing(true);
      setError('');
      setFinding(null);
      const res = await researchService.createResearchQuestion(targetDatasetId, questionText, predictorVar, targetVar);
      if (res && res.data) {
        setCurrentRq(res.data);
        setExistingQuestions((prev) => [res.data, ...prev]);
        if (refreshWorkflowStatus) refreshWorkflowStatus();
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to formulate hypotheses.');
    } finally {
      setAnalyzing(false);
    }
  };

  const handleExecuteTest = async (hypothesisId) => {
    if (!currentRq) return;

    try {
      setTestingId(hypothesisId);
      setError('');
      const res = await researchService.executeHypothesisTest(currentRq.id, hypothesisId);
      if (res && res.data) {
        setFinding(res.data);
        // Refresh question to reflect test state
        const updatedRq = await researchService.getResearchQuestion(currentRq.id);
        if (updatedRq && updatedRq.data) setCurrentRq(updatedRq.data);
        refreshWorkflowStatus();
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Statistical test execution failed.');
    } finally {
      setTestingId(null);
    }
  };

  if (loading) {
    return (
      <div className="container py-5 text-center">
        <div className="spinner-border text-primary" role="status"></div>
        <p className="text-muted small mt-2">Loading scientific inquiry workspace...</p>
      </div>
    );
  }

  const columns = dataset?.columns || [];

  return (
    <div className="container py-4">
      <WorkflowNavigator currentStage={currentStage}>
        {/* Breadcrumb */}
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb small">
            <li className="breadcrumb-item"><Link to="/datasets">Datasets</Link></li>
            <li className="breadcrumb-item"><Link to={`/datasets/${id}`}>{dataset?.name || 'Dataset'}</Link></li>
            <li className="breadcrumb-item active" aria-current="page">Research Questions & Hypotheses</li>
          </ol>
        </nav>

      <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
        <div>
          <h2 className="fw-bold mb-1">
            <i className="bi bi-question-circle-fill text-primary me-2"></i>Research & Hypothesis Engine
          </h2>
          <p className="text-muted small mb-0">
            Formulate empirical hypotheses, execute rigorous statistical tests, and synthesize evidence graphs.
          </p>
        </div>
        <Link to={`/datasets/${id}/associations`} className="btn btn-outline-success btn-sm">
          <i className="bi bi-diagram-3 me-1"></i> Associations Matrix
        </Link>
      </div>

      {error && (
        <div className="alert alert-danger" role="alert">
          <i className="bi bi-exclamation-triangle-fill me-2"></i>
          {error}
        </div>
      )}

      {/* Inquiry Formulation Form */}
      <div className="card shadow-sm border-0 mb-4 bg-white p-4">
        <h5 className="fw-bold mb-3">
          <i className="bi bi-pencil-square text-primary me-2"></i>Pose Empirical Research Question
        </h5>
        <form onSubmit={handleFormulate}>
          <div className="mb-3">
            <label className="form-label small fw-semibold">Scientific Question</label>
            <input
              type="text"
              className="form-control"
              placeholder="e.g. Does study_hours predict exam_score?"
              value={questionText}
              onChange={(e) => setQuestionText(e.target.value)}
              required
            />
            <div className="form-text small">
              Specify your scientific inquiry in plain English or select the target and predictor features below.
            </div>
          </div>

          <div className="row g-3 mb-4">
            <div className="col-md-6">
              <label className="form-label small fw-semibold">Predictor Variable (X)</label>
              <select
                className="form-select form-select-sm"
                value={predictorVar}
                onChange={(e) => setPredictorVar(e.target.value)}
              >
                {columns.map((c) => (
                  <option key={c.columnName} value={c.columnName}>
                    {c.columnName} ({c.dataType})
                  </option>
                ))}
              </select>
            </div>
            <div className="col-md-6">
              <label className="form-label small fw-semibold">Target Variable (Y)</label>
              <select
                className="form-select form-select-sm"
                value={targetVar}
                onChange={(e) => setTargetVar(e.target.value)}
              >
                {columns.map((c) => (
                  <option key={c.columnName} value={c.columnName}>
                    {c.columnName} ({c.dataType})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary d-flex align-items-center gap-2"
            disabled={analyzing}
          >
            {analyzing ? (
              <>
                <span className="spinner-border spinner-border-sm" role="status"></span>
                <span>Synthesizing Hypotheses...</span>
              </>
            ) : (
              <>
                <i className="bi bi-lightning-charge-fill"></i>
                <span>Analyze Question & Formulate Hypotheses</span>
              </>
            )}
          </button>
        </form>
      </div>

      {/* Active Research Question Details & Hypotheses */}
      {currentRq && (
        <div className="card shadow-sm border-0 mb-4 bg-white p-4">
          <div className="d-flex justify-content-between align-items-start mb-3 flex-wrap gap-2">
            <div>
              <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 mb-2">
                Active Research Question #{currentRq.id}
              </span>
              <h4 className="fw-bold mb-1">"{currentRq.questionText}"</h4>
              <p className="text-muted small mb-0">
                Predictor: <strong className="text-dark">{currentRq.predictorVariable}</strong> &bull;{' '}
                Target: <strong className="text-dark">{currentRq.targetVariable}</strong> &bull;{' '}
                Recommended Test: <span className="badge bg-info text-dark">{currentRq.suggestedTest}</span>
              </p>
            </div>
          </div>

          <hr className="my-3" />

          <h5 className="fw-bold mb-3">Formulated Hypotheses</h5>
          {currentRq.hypotheses?.map((h) => (
            <div key={h.id} className="card border p-3 mb-3 bg-light">
              <div className="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
                <span className="badge bg-secondary">Hypothesis #{h.rankOrder}</span>
                <span className="text-muted small">Confidence Threshold: &alpha; = 0.05</span>
              </div>

              <div className="mb-2">
                <strong className="text-muted small d-block">NULL HYPOTHESIS (H0):</strong>
                <span className="fst-italic text-dark">{h.nullHypothesis}</span>
              </div>

              <div className="mb-2">
                <strong className="text-primary small d-block">ALTERNATIVE HYPOTHESIS (H1):</strong>
                <span className="fw-semibold text-dark">{h.altHypothesis}</span>
              </div>

              <div className="mb-3">
                <strong className="text-muted small d-block">SCIENTIFIC RATIONALE:</strong>
                <span className="text-secondary small">{h.rationale}</span>
              </div>

              <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 pt-2 border-top">
                <span className="badge bg-light text-dark border">
                  Architecture: {h.suggestedTest}
                </span>

                <button
                  className="btn btn-success btn-sm d-flex align-items-center gap-1"
                  onClick={() => handleExecuteTest(h.id)}
                  disabled={testingId === h.id}
                >
                  {testingId === h.id ? (
                    <>
                      <span className="spinner-border spinner-border-sm" role="status"></span>
                      <span>Computing Test...</span>
                    </>
                  ) : (
                    <>
                      <i className="bi bi-play-circle-fill"></i>
                      <span>Execute Empirical Statistical Test</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Real-time Finding & Evidence Graph */}
      {finding && (
        <div className="card shadow-sm border-0 mb-4 bg-white p-4 border-start border-4 border-success">
          <div className="d-flex justify-content-between align-items-start mb-2 flex-wrap gap-2">
            <div>
              <span className={`badge ${
                finding.confidenceLevel === 'HIGH' ? 'bg-success' :
                finding.confidenceLevel === 'MODERATE' ? 'bg-primary' :
                finding.confidenceLevel === 'WEAK' ? 'bg-warning text-dark' : 'bg-secondary'
              } mb-2`}>
                Evidence Confidence: {finding.confidenceLevel}
              </span>
              <h4 className="fw-bold text-dark mb-1">{finding.title}</h4>
            </div>
          </div>

          <p className="lead text-secondary fs-6 mb-3">{finding.summary}</p>

          <div className="card bg-light border p-3 mb-3">
            <h6 className="fw-bold mb-2">Empirical Test Metrics & Verification</h6>
            <pre className="mb-0 small text-dark font-monospace" style={{ whiteSpace: 'pre-wrap' }}>
              {finding.evidenceText}
            </pre>
          </div>

          {/* Evidence Graph Vertices */}
          <h6 className="fw-bold mb-2">Provenance Graph (Evidence Trail)</h6>
          <div className="row g-2 mb-3">
            {finding.evidenceNodes?.map((node) => (
              <div key={node.id} className="col-md-4">
                <div className="p-2 border rounded bg-white small h-100 shadow-sm">
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <span className="badge bg-dark">{node.nodeType}</span>
                    <span className="text-muted small">Ref #{node.referenceId}</span>
                  </div>
                  <span className="text-secondary d-block">{node.description}</span>
                </div>
              </div>
            ))}
          </div>

          {/* Causality Disclaimer */}
          <div className="alert alert-warning py-2 px-3 small d-flex align-items-center gap-2 mb-0" role="alert">
            <i className="bi bi-shield-exclamation fs-5"></i>
            <div>
              <strong>Scientific Causality Disclaimer: </strong>
              {finding.causalityDisclaimer}
            </div>
          </div>
        </div>
      )}

      {/* Past Research Inquiries Table */}
      {existingQuestions.length > 1 && (
        <div className="card shadow-sm border-0 bg-white">
          <div className="card-header bg-white py-3">
            <h5 className="fw-bold mb-0">Prior Questions on this Dataset</h5>
          </div>
          <div className="list-group list-group-flush">
            {existingQuestions.map((q) => (
              <button
                key={q.id}
                className={`list-group-item list-group-item-action text-start p-3 ${currentRq?.id === q.id ? 'active' : ''}`}
                onClick={() => {
                  setCurrentRq(q);
                  setFinding(null);
                }}
              >
                <div className="d-flex justify-content-between align-items-center">
                  <span className="fw-semibold">"{q.questionText}"</span>
                  <span className={`badge ${currentRq?.id === q.id ? 'bg-light text-dark' : 'bg-primary'}`}>
                    {q.suggestedTest}
                  </span>
                </div>
                <div className="small opacity-75 mt-1">
                  Predictor: {q.predictorVariable} &bull; Target: {q.targetVariable} &bull; Hypotheses: {q.hypotheses?.length || 0}
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
      </WorkflowNavigator>
    </div>
  );
};

export default ResearchWorkflowPage;
