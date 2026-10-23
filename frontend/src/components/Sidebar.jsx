import React, { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useWorkflow } from '../context/WorkflowContext';

const STORAGE_KEY = 'autoscientist_sidebar_collapsed';

const buildGroups = (datasetId) => [
  {
    label: 'Overview',
    items: [
      { to: '/dashboard', icon: 'bi-speedometer2', label: 'Dashboard', end: true, gated: false },
      { to: '/datasets', icon: 'bi-database', label: 'Datasets', gated: false },
    ],
  },
  {
    label: 'Discover',
    items: [
      { to: datasetId ? `/datasets/${datasetId}/associations` : '/associations', icon: 'bi-diagram-2', label: 'Associations', gated: true },
      { to: datasetId ? `/datasets/${datasetId}/research` : '/research', icon: 'bi-journal-text', label: 'Research', gated: true },
      { to: datasetId ? `/datasets/${datasetId}/visualizations` : '/visualizations', icon: 'bi-pie-chart', label: 'Visualizations', gated: true },
    ],
  },
  {
    label: 'Model & Explain',
    items: [
      { to: datasetId ? `/datasets/${datasetId}/ml` : '/automl', icon: 'bi-cpu', label: 'AutoML & XAI', gated: true },
      { to: '/experiments', icon: 'bi-layers', label: 'Experiments', gated: true },
      { to: datasetId ? `/datasets/${datasetId}/ask` : '/ask', icon: 'bi-chat-square-text', label: 'Ask Dataset', gated: true },
      { to: '/drift', icon: 'bi-graph-down-arrow', label: 'Drift Monitor', gated: true },
    ],
  },
  {
    label: 'Communicate',
    items: [
      { to: datasetId ? `/datasets/${datasetId}/findings` : '/findings', icon: 'bi-award', label: 'Findings', gated: true },
      { to: datasetId ? `/datasets/${datasetId}/report` : '/reports', icon: 'bi-file-earmark-pdf', label: 'Reports', gated: true },
    ],
  },
];

const getStoredCollapsed = () => {
  try {
    return typeof window !== 'undefined' && window.localStorage && typeof window.localStorage.getItem === 'function'
      ? window.localStorage.getItem(STORAGE_KEY) === 'true'
      : false;
  } catch (e) {
    return false;
  }
};

export default function Sidebar() {
  const { dataset, datasetId, hasDatasets, datasetsLoading } = useWorkflow();
  const [collapsed, setCollapsed] = useState(getStoredCollapsed);
  const activeId = dataset?.id || datasetId;

  useEffect(() => {
    try {
      if (typeof window !== 'undefined' && window.localStorage && typeof window.localStorage.setItem === 'function') {
        window.localStorage.setItem(STORAGE_KEY, String(collapsed));
      }
    } catch (e) {
      // Ignore localStorage access failures in restricted environments
    }
  }, [collapsed]);

  const groups = buildGroups(activeId);
  const isLocked = !hasDatasets && !datasetsLoading;

  return (
    <aside className={`app-sidebar d-none d-lg-flex flex-column ${collapsed ? 'is-collapsed' : ''}`}>
      <div className={`app-sidebar-header ${collapsed ? 'justify-content-center' : 'justify-content-between'}`}>
        {!collapsed && <span className="text-uppercase text-muted small fw-semibold">Workbench</span>}
        <button
          type="button"
          className="btn btn-sm btn-link text-decoration-none text-secondary p-1"
          onClick={() => setCollapsed((v) => !v)}
          aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}
          title={collapsed ? 'Expand navigation' : 'Collapse navigation'}
        >
          <i className={`bi ${collapsed ? 'bi-chevron-double-right' : 'bi-chevron-double-left'}`}></i>
        </button>
      </div>

      <nav className="app-sidebar-nav flex-grow-1">
        {groups.map((group) => (
          <div key={group.label} className="app-sidebar-group">
            {!collapsed && <div className="app-sidebar-group-label">{group.label}</div>}
            {group.items.map((item) => {
              const itemDisabled = item.gated && isLocked;
              const lockPrompt = 'Upload a dataset to unlock scientific analysis.';

              if (itemDisabled) {
                return (
                  <span
                    key={item.label}
                    className={`app-sidebar-link is-disabled ${collapsed ? 'justify-content-center' : ''}`}
                    title={collapsed ? `${item.label} (${lockPrompt})` : lockPrompt}
                    aria-disabled="true"
                    data-testid={`sidebar-locked-${item.label.toLowerCase().replace(/[^a-z0-9]/g, '-')}`}
                  >
                    <i className={`bi ${item.icon} app-sidebar-icon`}></i>
                    {!collapsed && (
                      <>
                        <span className="text-truncate">{item.label}</span>
                        <i
                          className="bi bi-lock-fill app-sidebar-lock-badge"
                          title={lockPrompt}
                          aria-hidden="true"
                        ></i>
                      </>
                    )}
                  </span>
                );
              }

              return (
                <NavLink
                  key={item.label}
                  to={item.to}
                  end={item.end}
                  title={collapsed ? item.label : undefined}
                  className={({ isActive }) =>
                    `app-sidebar-link ${isActive ? 'active' : ''} ${collapsed ? 'justify-content-center' : ''}`
                  }
                >
                  <i className={`bi ${item.icon} app-sidebar-icon`}></i>
                  {!collapsed && <span className="text-truncate">{item.label}</span>}
                </NavLink>
              );
            })}
          </div>
        ))}

        {!collapsed && isLocked && (
          <div
            className="p-3 mx-2 my-2 bg-light rounded border text-center"
            data-testid="sidebar-upload-cta-card"
          >
            <div className="small fw-semibold text-secondary mb-1">
              <i className="bi bi-lock-fill text-warning me-1"></i> Analysis Locked
            </div>
            <div className="text-muted mb-2" style={{ fontSize: '0.76rem' }}>
              Upload a dataset to unlock scientific analysis.
            </div>
            <NavLink
              to="/datasets"
              className="btn btn-primary btn-sm w-100 py-1 d-flex align-items-center justify-content-center gap-1"
              style={{ fontSize: '0.8rem' }}
            >
              <i className="bi bi-cloud-arrow-up-fill"></i>
              <span>Upload Dataset</span>
            </NavLink>
          </div>
        )}
      </nav>

      {!collapsed && (
        <div className="app-sidebar-footer">
          {isLocked ? (
            <span className="small text-muted d-flex align-items-center gap-1">
              <i className="bi bi-lock-fill text-warning"></i>
              <span className="text-truncate">Upload a dataset to unlock.</span>
            </span>
          ) : activeId ? (
            <span className="small text-muted text-truncate d-block">
              <i className="bi bi-folder2-open me-1"></i>
              {dataset?.name || `Dataset #${activeId}`}
            </span>
          ) : (
            <span className="small text-muted text-truncate d-block">
              <i className="bi bi-info-circle me-1"></i>
              Select a dataset to begin analysis.
            </span>
          )}
        </div>
      )}
    </aside>
  );
}
