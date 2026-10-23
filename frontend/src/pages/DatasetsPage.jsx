import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { datasetService } from '../services/datasetService';
import { useWorkflow } from '../context/WorkflowContext';
import ImportWizard from '../components/ImportWizard';

const DatasetsPage = () => {
  const { refreshUserDatasets, selectDataset } = useWorkflow();
  const [datasets, setDatasets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Import wizard modal (wizard owns file/name/description state)
  const [showUploadModal, setShowUploadModal] = useState(false);

  const navigate = useNavigate();

  const loadDatasets = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await datasetService.listDatasets();
      if (res && res.data) {
        setDatasets(res.data);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load datasets.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDatasets();
  }, []);

  const handleDelete = async (id, datasetName) => {
    if (!window.confirm(`Are you sure you want to delete dataset "${datasetName}"? This action cannot be undone.`)) {
      return;
    }

    try {
      await datasetService.deleteDataset(id);
      setSuccess(`Dataset "${datasetName}" deleted.`);
      await loadDatasets();
      if (refreshUserDatasets) {
        await refreshUserDatasets();
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete dataset.');
    }
  };

  const getScoreBadge = (score) => {
    if (score == null) return <span className="badge bg-secondary">N/A</span>;
    if (score >= 80) return <span className="badge bg-success">{score.toFixed(1)} / 100</span>;
    if (score >= 50) return <span className="badge bg-warning text-dark">{score.toFixed(1)} / 100</span>;
    return <span className="badge bg-danger">{score.toFixed(1)} / 100</span>;
  };

  return (
    <div className="container py-4">
      <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
        <div>
          <h2 className="fw-bold mb-1">
            <i className="bi bi-database text-primary me-2"></i>Dataset Repository
          </h2>
          <p className="text-muted small mb-0">
            Upload, profile, and inspect empirical datasets for automated scientific exploration.
          </p>
        </div>
        <button
          className="btn btn-primary d-flex align-items-center gap-2"
          onClick={() => setShowUploadModal(true)}
        >
          <i className="bi bi-cloud-arrow-up-fill"></i>
          <span>Upload Dataset</span>
        </button>
      </div>

      {error && (
        <div className="alert alert-danger alert-dismissible fade show" role="alert">
          <i className="bi bi-exclamation-triangle-fill me-2"></i>
          {error}
          <button type="button" className="btn-close" onClick={() => setError('')}></button>
        </div>
      )}

      {success && (
        <div className="alert alert-success alert-dismissible fade show" role="alert">
          <i className="bi bi-check-circle-fill me-2"></i>
          {success}
          <button type="button" className="btn-close" onClick={() => setSuccess('')}></button>
        </div>
      )}

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading datasets...</span>
          </div>
          <p className="text-muted mt-2 small">Scanning dataset repository...</p>
        </div>
      ) : datasets.length === 0 ? (
        <div className="card text-center py-5 border-dashed">
          <div className="card-body">
            <i className="bi bi-folder-x display-4 text-muted mb-3 d-block"></i>
            <h5 className="fw-bold">No Datasets Ingested Yet</h5>
            <p className="text-muted small max-w-md mx-auto">
              Upload your first CSV or Excel file to trigger automated data quality profiling,
              hypothesis generation, and machine learning pipelines.
            </p>
            <button
              className="btn btn-outline-primary mt-2"
              onClick={() => setShowUploadModal(true)}
            >
              <i className="bi bi-upload me-1"></i> Upload First Dataset
            </button>
          </div>
        </div>
      ) : (
        <div className="card shadow-sm border-0">
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th scope="col" className="ps-3">Dataset Name</th>
                  <th scope="col">Rows</th>
                  <th scope="col">Columns</th>
                  <th scope="col">Macro Type</th>
                  <th scope="col">Quality Score</th>
                  <th scope="col">Created</th>
                  <th scope="col" className="text-end pe-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {datasets.map((d) => (
                  <tr key={d.id}>
                    <td className="ps-3">
                      <Link to={`/datasets/${d.id}/overview`} className="fw-semibold text-decoration-none text-primary">
                        {d.name}
                      </Link>
                      <div className="text-muted small text-truncate" style={{ maxWidth: '240px' }}>
                        {d.originalFilename}
                      </div>
                    </td>
                    <td><span className="badge bg-light text-dark border">{d.rowCount?.toLocaleString()}</span></td>
                    <td><span className="badge bg-light text-dark border">{d.columnCount} cols</span></td>
                    <td>
                      <span className="badge bg-info bg-opacity-10 text-info border border-info border-opacity-25">
                        {d.datasetType || 'MIXED'}
                      </span>
                    </td>
                    <td>{getScoreBadge(d.qualityScore)}</td>
                    <td className="text-muted small">
                      {d.createdAt ? new Date(d.createdAt).toLocaleDateString() : 'N/A'}
                    </td>
                    <td className="text-end pe-3 text-nowrap">
                      {/* Full labelled actions: tablet and up */}
                      <div className="btn-group btn-group-sm d-none d-md-flex" data-testid="row-actions-desktop">
                        <Link
                          to={`/datasets/${d.id}/overview`}
                          className="btn btn-outline-primary"
                          title="Inspect Profile & EDA"
                        >
                          <i className="bi bi-graph-up me-1"></i> Inspect
                        </Link>
                        <Link
                          to={`/datasets/${d.id}/associations`}
                          className="btn btn-outline-success"
                          title="Find Associations"
                        >
                          <i className="bi bi-diagram-3 me-1"></i> Associations
                        </Link>
                        <button
                          onClick={() => handleDelete(d.id, d.name)}
                          className="btn btn-outline-danger"
                          title="Delete Dataset"
                          aria-label={`Delete dataset ${d.name}`}
                        >
                          <i className="bi bi-trash"></i>
                        </button>
                      </div>

                      {/* Compact icon actions: mobile only (<768px), so the
                          three actions never wrap at narrow viewports */}
                      <div className="btn-group btn-group-sm d-flex d-md-none" data-testid="row-actions-mobile">
                        <Link
                          to={`/datasets/${d.id}/overview`}
                          className="btn btn-outline-primary"
                          title="Inspect Profile & EDA"
                          aria-label={`Inspect dataset ${d.name}`}
                        >
                          <i className="bi bi-graph-up"></i>
                        </Link>
                        <Link
                          to={`/datasets/${d.id}/associations`}
                          className="btn btn-outline-success"
                          title="Find Associations"
                          aria-label={`Associations for dataset ${d.name}`}
                        >
                          <i className="bi bi-diagram-3"></i>
                        </Link>
                        <button
                          onClick={() => handleDelete(d.id, d.name)}
                          className="btn btn-outline-danger"
                          title="Delete Dataset"
                          aria-label={`Delete dataset ${d.name}`}
                        >
                          <i className="bi bi-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Import wizard modal */}
      {showUploadModal && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <ImportWizard
                onCancel={() => setShowUploadModal(false)}
                onImported={async (ds) => {
                  setShowUploadModal(false);
                  setSuccess('Dataset imported and profiled successfully!');
                  await loadDatasets();
                  if (refreshUserDatasets) {
                    await refreshUserDatasets();
                  }
                  if (ds && ds.id) {
                    if (selectDataset) {
                      selectDataset(ds.id);
                    }
                    navigate(`/datasets/${ds.id}/overview`);
                  }
                }}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DatasetsPage;
