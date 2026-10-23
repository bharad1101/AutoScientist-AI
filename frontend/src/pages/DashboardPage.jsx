import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useWorkflow } from '../context/WorkflowContext';
import { analysisService } from '../services/analysisService';
import { Bar } from 'react-chartjs-2';
import '../utils/chartSetup';

const EVENT_SERIES = [
  { key: 'datasetUploads', label: 'Datasets uploaded', color: 'rgba(37, 99, 235, 0.75)' },
  { key: 'experiments', label: 'AutoML experiments', color: 'rgba(245, 158, 11, 0.75)' },
  { key: 'findings', label: 'Findings produced', color: 'rgba(16, 185, 129, 0.75)' },
  { key: 'questions', label: 'Research questions', color: 'rgba(56, 189, 248, 0.75)' },
  { key: 'reports', label: 'Reports exported', color: 'rgba(239, 68, 68, 0.7)' },
];

const DashboardPage = () => {
  const { user } = useAuth();
  const { hasDatasets, datasetsLoading } = useWorkflow();
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  const isLocked = !hasDatasets && !datasetsLoading;

  useEffect(() => {
    loadDashboardStats();
  }, []);

  const loadDashboardStats = async () => {
    try {
      const res = await analysisService.getDashboardStats();
      setStats(res.data);
    } catch (err) {
      console.error('Failed to load dashboard stats', err);
    } finally {
      setLoading(false);
    }
  };

  // Daily research-activity histogram built from the persisted event timeline.
  const activityChart = useMemo(() => {
    const activity = stats?.activity;
    if (!activity || !Array.isArray(activity.buckets) || activity.buckets.length === 0) {
      return null;
    }
    const buckets = activity.buckets;
    const hasEvents = buckets.some((b) => (b.total || 0) > 0);
    if (!hasEvents) return null;

    return {
      activity,
      data: {
        labels: buckets.map((b) => b.date),
        datasets: EVENT_SERIES.map((s) => ({
          label: s.label,
          data: buckets.map((b) => b[s.key] || 0),
          backgroundColor: s.color,
          borderRadius: 2,
          borderWidth: 0,
        })),
      },
    };
  }, [stats]);

  return (
    <div className="container-fluid py-4 px-4">
      {/* Banner */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="card bg-dark text-white p-4 shadow-sm border-0 position-relative overflow-hidden">
            <div className="position-absolute end-0 top-0 opacity-10 p-4 d-none d-md-block">
              <i className="bi bi-diagram-3-fill" style={{ fontSize: '10rem' }}></i>
            </div>
            <div className="position-relative">
              <span className="badge bg-primary mb-2 px-3 py-2">AutoScientist AI Platform</span>
              <h2 className="fw-bold mb-1">Welcome back, {user?.name || 'Researcher'}!</h2>
              <p className="text-secondary mb-3">
                Transform raw empirical datasets into verifiable, evidence-based scientific findings.
              </p>
              <div className="d-flex flex-wrap gap-2 align-items-center">
                <Link to="/datasets" className="btn btn-primary d-flex align-items-center gap-2">
                  <i className="bi bi-cloud-arrow-up-fill"></i>
                  <span>{isLocked ? 'Upload First Dataset' : 'Upload Dataset'}</span>
                </Link>
                {isLocked ? (
                  <span className="badge bg-secondary bg-opacity-50 text-light py-2 px-3 d-flex align-items-center gap-1">
                    <i className="bi bi-lock-fill text-warning"></i>
                    <span>Upload a dataset to unlock scientific analysis.</span>
                  </span>
                ) : (
                  <>
                    <Link to="/automl" className="btn btn-outline-light d-flex align-items-center gap-2">
                      <i className="bi bi-cpu"></i>
                      <span>AutoML & XAI</span>
                    </Link>
                    <Link to="/ask" className="btn btn-outline-light d-flex align-items-center gap-2">
                      <i className="bi bi-chat-left-dots"></i>
                      <span>Ask Dataset</span>
                    </Link>
                    <Link to="/reports" className="btn btn-outline-light d-flex align-items-center gap-2">
                      <i className="bi bi-file-earmark-pdf"></i>
                      <span>Scientific Reports</span>
                    </Link>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Live Metric Cards */}
      <div className="row g-4 mb-4">
        <div className="col-md-3">
          <div className="card shadow-sm border-0 h-100 p-3">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <span className="text-muted small fw-semibold">DATASETS & ROWS</span>
              <div className="p-2 bg-primary bg-opacity-10 text-primary rounded">
                <i className="bi bi-database"></i>
              </div>
            </div>
            <h3 className="fw-bold mb-0 text-primary">
              {loading ? '...' : stats?.totalDatasets ?? 0}
            </h3>
            <span className="text-muted small">
              {stats?.totalObservations ? `${stats.totalObservations.toLocaleString()} rows profiled` : 'No rows yet'}
            </span>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card shadow-sm border-0 h-100 p-3">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <span className="text-muted small fw-semibold">BIVARIATE ASSOCIATIONS</span>
              <div className="p-2 bg-success bg-opacity-10 text-success rounded">
                <i className="bi bi-diagram-2"></i>
              </div>
            </div>
            <h3 className="fw-bold mb-0 text-success">
              {loading ? '...' : stats?.totalAssociations ?? 0}
            </h3>
            <span className="text-muted small">Pearson, Welch, ANOVA, χ²</span>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card shadow-sm border-0 h-100 p-3">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <span className="text-muted small fw-semibold">MODELS BENCHMARKED</span>
              <div className="p-2 bg-warning bg-opacity-10 text-warning rounded">
                <i className="bi bi-cpu"></i>
              </div>
            </div>
            <h3 className="fw-bold mb-0 text-warning">
              {loading ? '...' : stats?.totalModelsTrained ?? 0}
            </h3>
            <span className="text-muted small">Ridge, Trees, Forests</span>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card shadow-sm border-0 h-100 p-3">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <span className="text-muted small fw-semibold">AVERAGE DATA QUALITY</span>
              <div className="p-2 bg-info bg-opacity-10 text-info rounded">
                <i className="bi bi-shield-check"></i>
              </div>
            </div>
            <h3 className="fw-bold mb-0 text-info">
              {loading ? '...' : `${stats?.averageQualityScore ?? 0} / 100`}
            </h3>
            <span className="text-muted small">Audited Quality Index</span>
          </div>
        </div>
      </div>

      {isLocked && (
        <div className="card shadow-sm border-0 p-4 mb-4 text-center" data-testid="dashboard-no-datasets-card">
          <div className="py-3">
            <i className="bi bi-folder2-open display-5 text-primary mb-3 d-block"></i>
            <h4 className="fw-bold">No Datasets Ingested Yet</h4>
            <p className="text-muted mx-auto mb-3" style={{ maxWidth: '540px' }}>
              Upload your first empirical dataset to unlock automated data profiling, data quality auditing,
              bivariate association detection, hypothesis testing, and predictive machine learning.
            </p>
            <Link to="/datasets" className="btn btn-primary d-inline-flex align-items-center gap-2 px-4 py-2">
              <i className="bi bi-cloud-arrow-up-fill"></i>
              <span>Upload Dataset</span>
            </Link>
          </div>
        </div>
      )}

      {/* Research Activity Over Time */}
      <div className="card shadow-sm border-0 mb-4">
        <div className="card-header bg-white py-3 border-0 d-flex flex-wrap justify-content-between align-items-center gap-2">
          <div>
            <h5 className="fw-bold mb-0">
              <i className="bi bi-graph-up text-primary me-2"></i>
              Research Activity Over Time
            </h5>
            <small className="text-muted">
              Daily count of recorded research events (datasets, experiments, findings, questions, reports).
            </small>
          </div>
          {activityChart && (
            <span className="badge bg-light text-dark border">
              {activityChart.activity.totalEvents} event(s) in window
              {activityChart.activity.truncated ? ' (most recent 60 days)' : ''}
            </span>
          )}
        </div>
        <div className="card-body p-4">
          {loading ? (
            <div className="text-center py-4">
              <div className="spinner-border spinner-border-sm text-primary mb-2"></div>
              <div className="small text-muted">Aggregating recorded activity...</div>
            </div>
          ) : activityChart ? (
            <>
              <div style={{ height: '300px' }}>
                <Bar
                  data={activityChart.data}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { position: 'bottom' } },
                    scales: {
                      x: {
                        stacked: true,
                        title: { display: true, text: 'Day' },
                        ticks: {
                          autoSkip: true,
                          maxRotation: 0,
                          callback(value) {
                            const label = this.getLabelForValue(value);
                            return label.length > 5 ? label.slice(5) : label;
                          },
                        },
                      },
                      y: {
                        stacked: true,
                        beginAtZero: true,
                        title: { display: true, text: 'Recorded events' },
                        ticks: { precision: 0 },
                      },
                    },
                  }}
                />
              </div>
              <p className="small text-muted mb-0 mt-2">
                <i className="bi bi-info-circle me-1"></i>
                Window {activityChart.activity.startDate} → {activityChart.activity.endDate}.
                {activityChart.activity.lifetimeEvents} event(s) recorded in total
                {activityChart.activity.truncated
                  ? `; only the most recent 60 days are plotted.`
                  : '.'}
              </p>
            </>
          ) : (
            <div className="text-center py-4 text-muted">
              <i className="bi bi-calendar-x display-6 d-block mb-2"></i>
              <p className="small mb-0">No research activity has been recorded yet.</p>
            </div>
          )}
        </div>
      </div>

      {/* Provenance & Activity Timeline */}
      <div className="card shadow-sm border-0 mb-4">
        <div className="card-header bg-white py-3 border-0 d-flex justify-content-between align-items-center">
          <h5 className="fw-bold mb-0">
            <i className="bi bi-clock-history text-secondary me-2"></i>
            Autonomous Research Provenance & Audit Trail
          </h5>
          <span className="badge bg-light text-dark border">
            {stats?.recentActivity?.length ?? 0} Events Recorded
          </span>
        </div>
        <div className="card-body p-0">
          <div className="list-group list-group-flush">
            {(!stats?.recentActivity || stats.recentActivity.length === 0) ? (
              <div className="p-4 text-center text-muted">
                No research events recorded yet. Start by uploading a dataset.
              </div>
            ) : (
              stats.recentActivity.map((event, idx) => (
                <div key={idx} className="list-group-item p-3 d-flex align-items-start gap-3">
                  <div className="mt-1">
                    {event.eventType === 'DATASET_UPLOAD' && (
                      <i className="bi bi-cloud-arrow-up-fill text-primary fs-5"></i>
                    )}
                    {event.eventType === 'FINDING_PRODUCED' && (
                      <i className="bi bi-award-fill text-success fs-5"></i>
                    )}
                    {event.eventType === 'MODEL_TRAINED' && (
                      <i className="bi bi-cpu-fill text-warning fs-5"></i>
                    )}
                    {event.eventType === 'QUESTION_ASKED' && (
                      <i className="bi bi-question-circle-fill text-info fs-5"></i>
                    )}
                    {event.eventType === 'REPORT_EXPORTED' && (
                      <i className="bi bi-file-earmark-pdf-fill text-danger fs-5"></i>
                    )}
                  </div>
                  <div className="flex-grow-1">
                    <div className="d-flex justify-content-between align-items-center">
                      <h6 className="fw-semibold mb-1">{event.title}</h6>
                      <small className="text-muted">
                        {event.timestamp ? new Date(event.timestamp).toLocaleString() : ''}
                      </small>
                    </div>
                    <p className="text-muted mb-0 small">{event.description}</p>
                    {event.datasetName && (
                      <small className="text-primary mt-1 d-inline-block">
                        <i className="bi bi-database me-1"></i>
                        {event.datasetName}
                      </small>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
