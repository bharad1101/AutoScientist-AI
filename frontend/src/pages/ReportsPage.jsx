import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';
import WorkflowNavigator from '../components/WorkflowNavigator';
import { useWorkflow } from '../context/WorkflowContext';

export default function ReportsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { datasetId: workflowDatasetId, selectDataset, refreshWorkflowStatus } = useWorkflow();

  const activeDatasetId = id || workflowDatasetId;

  const [datasets, setDatasets] = useState([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState(activeDatasetId || '');
  const [selectedDataset, setSelectedDataset] = useState(null);
  const [reports, setReports] = useState([]);
  const [activeReport, setActiveReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [exporting, setExporting] = useState('');
  const [error, setError] = useState(null);

  const loadData = useCallback(async (targetId) => {
    if (!targetId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setSelectedDatasetId(targetId);
      if (selectDataset) selectDataset(targetId);

      // Load active dataset and its reports independently
      const [dsRes, repRes] = await Promise.all([
        datasetService.getDataset(targetId).catch((err) => {
          console.error('Failed to load dataset:', err);
          return null;
        }),
        analysisService
          .getDatasetReports(targetId)
          .catch(() => analysisService.listReports().catch(() => ({ data: [] }))),
      ]);

      if (dsRes && dsRes.data) {
        setSelectedDataset(dsRes.data);
      } else {
        setError('Unable to load the active dataset.');
      }

      if (repRes && repRes.data) {
        const dsReports = repRes.data.filter(
          (r) => !r.datasetId || r.datasetId.toString() === targetId.toString()
        );
        const finalReports = dsReports.length > 0 ? dsReports : repRes.data;
        setReports(finalReports);
        if (finalReports.length > 0) {
          setActiveReport(finalReports[0]);
        }
      }
    } catch (err) {
      console.error('Failed to load reports workspace:', err);
      setError(err.response?.data?.message || err.message || 'Unable to load report workspace.');
    } finally {
      setLoading(false);
    }
  }, [selectDataset]);

  useEffect(() => {
    if (activeDatasetId) {
      loadData(activeDatasetId);
    } else {
      datasetService
        .listDatasets()
        .then((res) => {
          if (res.data && res.data.length > 0) {
            setDatasets(res.data);
            loadData(res.data[0].id);
          } else {
            setLoading(false);
          }
        })
        .catch(() => setLoading(false));
    }

    // Populate dataset switcher in background
    datasetService
      .listDatasets()
      .then((res) => {
        if (res.data) setDatasets(res.data);
      })
      .catch(() => {});
  }, [activeDatasetId, loadData]);

  const handleGenerateReport = async () => {
    const targetId = selectedDatasetId || activeDatasetId;
    if (!targetId) return;

    setGenerating(true);
    setError(null);
    try {
      const res = await analysisService.generateReport(targetId, {});
      if (res && res.data) {
        setReports((prev) => [res.data, ...prev]);
        setActiveReport(res.data);
        if (refreshWorkflowStatus) refreshWorkflowStatus();
      }
    } catch (err) {
      console.error('Report generation error:', err);
      setError(err.response?.data?.message || err.message || 'Failed to generate scientific report.');
    } finally {
      setGenerating(false);
    }
  };

  const handleDownload = async (reportId, reportTitle, format = 'pdf') => {
    if (!reportId) return;
    const descriptor = {
      pdf: { call: () => analysisService.downloadReportPdf(reportId), type: 'application/pdf', ext: 'pdf' },
      csv: { call: () => analysisService.downloadReportCsv(reportId), type: 'text/csv;charset=utf-8', ext: 'csv' },
      excel: {
        call: () => analysisService.downloadReportExcel(reportId),
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        ext: 'xlsx',
      },
      markdown: {
        call: () => analysisService.downloadReportMarkdown(reportId),
        type: 'text/markdown;charset=utf-8',
        ext: 'md',
      },
    }[format];

    if (format === 'pdf') setDownloading(true);
    else setExporting(format);
    try {
      const blob = await descriptor.call();
      const url = window.URL.createObjectURL(new Blob([blob], { type: descriptor.type }));
      const link = document.createElement('a');
      link.href = url;
      const cleanTitle = (reportTitle || 'Scientific_Report').replace(/[^a-zA-Z0-9_-]/g, '_');
      link.setAttribute('download', `${cleanTitle}.${descriptor.ext}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error(`${format.toUpperCase()} download error:`, err);
      setError(`Failed to download ${format.toUpperCase()} report.`);
    } finally {
      if (format === 'pdf') setDownloading(false);
      else setExporting('');
    }
  };

  const handleDownloadPdf = (reportId, reportTitle) => handleDownload(reportId, reportTitle, 'pdf');

  return (
    <div className="container py-4">
      <WorkflowNavigator
        currentStage="report"
        onCustomNextAction={() => activeReport && handleDownloadPdf(activeReport.id, activeReport.title)}
      >
        <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
          <div>
            <h2 className="fw-bold mb-1">
              <i className="bi bi-file-earmark-pdf-fill text-danger me-2"></i>
              Scientific Discovery Reports & Vector PDF Export
            </h2>
            <p className="text-muted mb-0 small">
              Automated synthesis of hypotheses, statistical decisions, ML benchmarks, and provenance graphs.
            </p>
          </div>

          <div className="d-flex align-items-center gap-2">
            {selectedDataset && (
              <span className="badge bg-light text-dark border p-2">
                <i className="bi bi-database me-1 text-primary"></i>
                <strong>{selectedDataset.name}</strong> ({selectedDataset.rowCount} rows)
              </span>
            )}

            {datasets.length > 1 && (
              <select
                className="form-select form-select-sm"
                value={selectedDatasetId}
                onChange={(e) => {
                  const newId = e.target.value;
                  navigate(`/datasets/${newId}/report`);
                }}
                disabled={generating}
                style={{ width: '180px' }}
              >
                {datasets.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            )}

            <button
              className="btn btn-primary fw-semibold px-4 text-nowrap shadow-sm"
              onClick={handleGenerateReport}
              disabled={generating || !selectedDatasetId}
            >
              {generating ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                  Synthesizing Report...
                </>
              ) : (
                <>
                  <i className="bi bi-magic me-2"></i>
                  Generate Scientific Report
                </>
              )}
            </button>
          </div>
        </div>

        {error && (
          <div className="alert alert-danger shadow-sm d-flex justify-content-between align-items-center mb-4" role="alert">
            <div>
              <i className="bi bi-exclamation-triangle-fill me-2"></i>
              {error}
            </div>
            <div className="d-flex gap-2">
              <button
                className="btn btn-sm btn-outline-danger"
                onClick={() => loadData(selectedDatasetId || activeDatasetId)}
              >
                <i className="bi bi-arrow-clockwise me-1"></i> Retry
              </button>
              <button type="button" className="btn-close" onClick={() => setError(null)}></button>
            </div>
          </div>
        )}

        {loading ? (
          <div className="text-center py-5">
            <div className="spinner-border text-primary mb-3" role="status"></div>
            <p className="text-muted small">Loading empirical reports and dataset evidence...</p>
          </div>
        ) : (
          <div className="row g-4">
            {/* Reports Sidebar */}
            <div className="col-lg-4">
              <div className="card shadow-sm border-0 h-100">
                <div className="card-header bg-white py-3 border-bottom d-flex justify-content-between align-items-center">
                  <h5 className="fw-bold mb-0">Generated Discovery Reports ({reports.length})</h5>
                  {reports.length > 0 && (
                    <span className="badge bg-success-subtle text-success border border-success-subtle small">
                      Ready
                    </span>
                  )}
                </div>
                <div className="list-group list-group-flush" style={{ maxHeight: '620px', overflowY: 'auto' }}>
                  {reports.length === 0 ? (
                    <div className="p-4 text-center text-muted">
                      <i className="bi bi-file-earmark-text fs-1 d-block mb-2 text-secondary"></i>
                      No reports generated for this dataset yet.
                      <div className="mt-3">
                        <button
                          className="btn btn-sm btn-primary fw-semibold"
                          onClick={handleGenerateReport}
                          disabled={generating}
                        >
                          Generate First Report
                        </button>
                      </div>
                    </div>
                  ) : (
                    reports.map((r) => (
                      <button
                        key={r.id}
                        type="button"
                        className={`list-group-item list-group-item-action p-3 text-start transition ${
                          activeReport?.id === r.id ? 'active' : ''
                        }`}
                        onClick={() => setActiveReport(r)}
                      >
                        <div className="d-flex justify-content-between align-items-center mb-1">
                          <span className="fw-semibold text-truncate" style={{ maxWidth: '200px' }}>
                            {r.title}
                          </span>
                          <small className={activeReport?.id === r.id ? 'text-white' : 'text-muted'}>
                            {new Date(r.createdAt).toLocaleDateString()}
                          </small>
                        </div>
                        <small
                          className={`d-block text-truncate ${
                            activeReport?.id === r.id ? 'text-white-50' : 'text-muted'
                          }`}
                        >
                          Dataset: {r.datasetName || selectedDataset?.name || 'Active'}
                        </small>
                      </button>
                    ))
                  )}
                </div>
              </div>
            </div>

            {/* Active Report Viewer */}
            <div className="col-lg-8">
              {activeReport ? (
                <div className="card shadow-sm border-0">
                  <div className="card-header bg-white py-3 border-bottom d-flex justify-content-between align-items-center flex-wrap gap-2">
                    <div>
                      <h4 className="fw-bold mb-1">{activeReport.title}</h4>
                      <span className="text-muted small">
                        Dataset: {activeReport.datasetName || selectedDataset?.name} &bull; Created:{' '}
                        {new Date(activeReport.createdAt).toLocaleString()}
                      </span>
                    </div>

                    <div className="d-flex flex-wrap gap-2">
                      <button
                        className="btn btn-outline-danger fw-semibold px-3 shadow-sm"
                        onClick={() => handleDownload(activeReport.id, activeReport.title, 'pdf')}
                        disabled={Boolean(exporting) || downloading}
                      >
                        {downloading ? (
                          <>
                            <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                            Exporting PDF...
                          </>
                        ) : (
                          <>
                            <i className="bi bi-file-earmark-pdf me-2"></i>
                            PDF
                          </>
                        )}
                      </button>
                      <button
                        className="btn btn-outline-success fw-semibold px-3 shadow-sm"
                        onClick={() => handleDownload(activeReport.id, activeReport.title, 'csv')}
                        disabled={Boolean(exporting) || downloading}
                        title="Download every markdown table as CSV"
                      >
                        {exporting === 'csv' ? (
                          <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                        ) : (
                          <i className="bi bi-filetype-csv me-2"></i>
                        )}
                        CSV
                      </button>
                      <button
                        className="btn btn-outline-primary fw-semibold px-3 shadow-sm"
                        onClick={() => handleDownload(activeReport.id, activeReport.title, 'excel')}
                        disabled={Boolean(exporting) || downloading}
                        title="Download every markdown table as a worksheet"
                      >
                        {exporting === 'excel' ? (
                          <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                        ) : (
                          <i className="bi bi-file-earmark-excel me-2"></i>
                        )}
                        Excel
                      </button>
                      <button
                        className="btn btn-outline-secondary fw-semibold px-3 shadow-sm"
                        onClick={() => handleDownload(activeReport.id, activeReport.title, 'markdown')}
                        disabled={Boolean(exporting) || downloading}
                        title="Download the full report as Markdown"
                      >
                        {exporting === 'markdown' ? (
                          <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                        ) : (
                          <i className="bi bi-filetype-md me-2"></i>
                        )}
                        Markdown
                      </button>
                    </div>
                  </div>

                  <div className="card-body p-4">
                    <div
                      className="p-4 bg-light rounded border"
                      style={{
                        maxHeight: '650px',
                        overflowY: 'auto',
                        whiteSpace: 'pre-wrap',
                        fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
                        lineHeight: '1.7',
                      }}
                    >
                      {activeReport.markdownContent}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="card shadow-sm border-0 p-5 text-center text-muted">
                  <i className="bi bi-journal-text fs-1 d-block mb-3 text-secondary"></i>
                  <h5>No Report Selected</h5>
                  <p className="small mb-3">Click "Generate Scientific Report" above to synthesize an empirical report from available analysis stages.</p>
                  <div>
                    <button
                      className="btn btn-primary fw-semibold px-4 py-2"
                      onClick={handleGenerateReport}
                      disabled={generating || !selectedDatasetId}
                    >
                      {generating ? 'Synthesizing...' : 'Generate Scientific Report'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </WorkflowNavigator>
    </div>
  );
}
