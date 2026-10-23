import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useWorkflow } from '../context/WorkflowContext';

/**
 * Route guard that ensures the authenticated user has at least one persisted dataset
 * before granting access to analysis-only workflows (EDA, Associations, Research,
 * AutoML, Visualizations, XAI, Findings, Reports, Drift, Ask Dataset, etc.).
 *
 * Avoids loading flashes while checking dataset status, and displays an informative
 * locked screen with working CTAs when no datasets are present.
 */
export default function RequireDatasetRoute({ children }) {
  const { loading: authLoading } = useAuth();
  const { hasDatasets, datasetsLoading } = useWorkflow();

  if (authLoading || datasetsLoading) {
    return (
      <div
        className="container text-center py-5 my-5"
        role="status"
        aria-label="Checking dataset status"
        data-testid="dataset-guard-loading"
      >
        <div className="spinner-border text-primary mb-3"></div>
        <p className="text-muted fw-semibold">Checking dataset access...</p>
      </div>
    );
  }

  if (!hasDatasets) {
    return (
      <div className="container py-5" data-testid="dataset-guard-locked">
        <div className="row justify-content-center">
          <div className="col-12 col-md-10 col-lg-8">
            <div className="card shadow-sm border-0 text-center p-4 p-md-5">
              <div className="mb-3">
                <span
                  className="d-inline-flex align-items-center justify-content-center bg-warning bg-opacity-10 text-warning rounded-circle p-3"
                  style={{ width: '72px', height: '72px' }}
                >
                  <i className="bi bi-lock-fill fs-1"></i>
                </span>
              </div>
              <h3 className="fw-bold mb-2">Analysis Workspace Locked</h3>
              <p className="lead text-secondary mb-3">
                Upload a dataset to unlock scientific analysis.
              </p>
              <p className="text-muted small mb-4 mx-auto" style={{ maxWidth: '520px' }}>
                AutoScientist AI requires at least one validated empirical dataset before you can access
                exploratory data analysis, bivariate associations, hypothesis testing, predictive machine learning,
                algorithmic fairness audits, or publication-ready reports.
              </p>
              <div className="d-flex justify-content-center flex-wrap gap-2">
                <Link
                  to="/datasets"
                  className="btn btn-primary d-flex align-items-center gap-2 px-4 py-2"
                >
                  <i className="bi bi-cloud-arrow-up-fill"></i>
                  <span>Upload Dataset</span>
                </Link>
                <Link
                  to="/dashboard"
                  className="btn btn-outline-secondary d-flex align-items-center gap-2 px-4 py-2"
                >
                  <i className="bi bi-speedometer2"></i>
                  <span>Go to Dashboard</span>
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return children;
}
