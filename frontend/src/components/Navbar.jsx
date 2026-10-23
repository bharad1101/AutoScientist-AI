import React, { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useWorkflow } from '../context/WorkflowContext';

const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const { dataset, hasDatasets, datasetsLoading } = useWorkflow();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const isLocked = !hasDatasets && !datasetsLoading;

  // React-owned collapse: Bootstrap JS is not loaded, so the menu state is
  // the single source of truth and always closes after in-menu navigation.
  const closeMobileMenu = () => setMobileOpen(false);

  const handleLogout = async () => {
    closeMobileMenu();
    await logout();
    navigate('/login');
  };

  return (
    <nav className="navbar navbar-expand-lg navbar-dark bg-dark px-3 px-lg-4 shadow-sm">
      <div className="container-fluid">
        <Link
          className="navbar-brand fw-bold text-primary d-flex align-items-center gap-2"
          to={isAuthenticated ? '/dashboard' : '/'}
        >
          <i className="bi bi-cpu-fill fs-4"></i>
          <span className="fs-5 tracking-tight text-white">AutoScientist <span className="text-primary">AI</span></span>
        </Link>
        <span className="navbar-text text-light opacity-75 small d-none d-xl-inline-block border-start ps-3 ms-2">
          "From Raw Data to Evidence-Based Scientific Discovery."
        </span>

        <button
          className="navbar-toggler"
          type="button"
          onClick={() => setMobileOpen((open) => !open)}
          aria-controls="navbarMain"
          aria-expanded={mobileOpen}
          aria-label="Toggle navigation"
        >
          <span className="navbar-toggler-icon"></span>
        </button>

        <div className={`collapse navbar-collapse ${mobileOpen ? 'show' : ''}`} id="navbarMain">
          {isAuthenticated ? (
            <>
              <ul className="navbar-nav me-auto mb-2 mb-lg-0 ms-lg-3 d-lg-none">
                <li className="nav-item">
                  <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to="/dashboard">
                    <i className="bi bi-speedometer2 me-1"></i> Dashboard
                  </NavLink>
                </li>
                <li className="nav-item">
                  <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to="/datasets">
                    <i className="bi bi-database me-1"></i> Datasets
                  </NavLink>
                </li>
                {isLocked && (
                  <li className="nav-item my-2">
                    <NavLink
                      onClick={closeMobileMenu}
                      className="btn btn-warning btn-sm w-100 text-dark d-flex align-items-center justify-content-center gap-2 fw-semibold"
                      to="/datasets"
                    >
                      <i className="bi bi-cloud-arrow-up-fill"></i>
                      <span>Upload Dataset to Unlock</span>
                    </NavLink>
                  </li>
                )}
                <li className="nav-item">
                  {isLocked ? (
                    <span
                      className="nav-link text-muted opacity-50 d-flex align-items-center justify-content-between"
                      title="Upload a dataset to unlock scientific analysis."
                      style={{ cursor: 'not-allowed' }}
                      aria-disabled="true"
                    >
                      <span><i className="bi bi-diagram-2 me-1"></i> Associations</span>
                      <i className="bi bi-lock-fill text-warning small"></i>
                    </span>
                  ) : (
                    <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to={dataset ? `/datasets/${dataset.id}/associations` : "/associations"}>
                      <i className="bi bi-diagram-2 me-1"></i> Associations
                    </NavLink>
                  )}
                </li>
                <li className="nav-item">
                  {isLocked ? (
                    <span
                      className="nav-link text-muted opacity-50 d-flex align-items-center justify-content-between"
                      title="Upload a dataset to unlock scientific analysis."
                      style={{ cursor: 'not-allowed' }}
                      aria-disabled="true"
                    >
                      <span><i className="bi bi-journal-text me-1"></i> Research</span>
                      <i className="bi bi-lock-fill text-warning small"></i>
                    </span>
                  ) : (
                    <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to={dataset ? `/datasets/${dataset.id}/research` : "/research"}>
                      <i className="bi bi-journal-text me-1"></i> Research
                    </NavLink>
                  )}
                </li>
                <li className="nav-item">
                  {isLocked ? (
                    <span
                      className="nav-link text-muted opacity-50 d-flex align-items-center justify-content-between"
                      title="Upload a dataset to unlock scientific analysis."
                      style={{ cursor: 'not-allowed' }}
                      aria-disabled="true"
                    >
                      <span><i className="bi bi-cpu me-1"></i> AutoML & XAI</span>
                      <i className="bi bi-lock-fill text-warning small"></i>
                    </span>
                  ) : (
                    <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to={dataset ? `/datasets/${dataset.id}/ml` : "/automl"}>
                      <i className="bi bi-cpu me-1"></i> AutoML & XAI
                    </NavLink>
                  )}
                </li>
                <li className="nav-item">
                  {isLocked ? (
                    <span
                      className="nav-link text-muted opacity-50 d-flex align-items-center justify-content-between"
                      title="Upload a dataset to unlock scientific analysis."
                      style={{ cursor: 'not-allowed' }}
                      aria-disabled="true"
                    >
                      <span><i className="bi bi-pie-chart me-1"></i> Visuals</span>
                      <i className="bi bi-lock-fill text-warning small"></i>
                    </span>
                  ) : (
                    <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to={dataset ? `/datasets/${dataset.id}/visualizations` : "/visualizations"}>
                      <i className="bi bi-pie-chart me-1"></i> Visuals
                    </NavLink>
                  )}
                </li>
                <li className="nav-item">
                  {isLocked ? (
                    <span
                      className="nav-link text-muted opacity-50 d-flex align-items-center justify-content-between"
                      title="Upload a dataset to unlock scientific analysis."
                      style={{ cursor: 'not-allowed' }}
                      aria-disabled="true"
                    >
                      <span><i className="bi bi-award me-1"></i> Findings</span>
                      <i className="bi bi-lock-fill text-warning small"></i>
                    </span>
                  ) : (
                    <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to={dataset ? `/datasets/${dataset.id}/findings` : "/findings"}>
                      <i className="bi bi-award me-1"></i> Findings
                    </NavLink>
                  )}
                </li>
                <li className="nav-item">
                  {isLocked ? (
                    <span
                      className="nav-link text-muted opacity-50 d-flex align-items-center justify-content-between"
                      title="Upload a dataset to unlock scientific analysis."
                      style={{ cursor: 'not-allowed' }}
                      aria-disabled="true"
                    >
                      <span><i className="bi bi-file-earmark-pdf me-1"></i> Reports</span>
                      <i className="bi bi-lock-fill text-warning small"></i>
                    </span>
                  ) : (
                    <NavLink onClick={closeMobileMenu} className={({ isActive }) => `nav-link ${isActive ? 'active text-white' : ''}`} to={dataset ? `/datasets/${dataset.id}/report` : "/reports"}>
                      <i className="bi bi-file-earmark-pdf me-1"></i> Reports
                    </NavLink>
                  )}
                </li>
              </ul>

              <div className="d-flex align-items-center gap-2">
                {dataset && (
                  <Link
                    onClick={closeMobileMenu}
                    to={`/datasets/${dataset.id}/overview`}
                    className="badge bg-primary bg-opacity-25 text-info text-decoration-none border border-info border-opacity-50 py-2 px-3 d-flex align-items-center gap-1 me-2"
                    title="Active Workflow Dataset"
                  >
                    <i className="bi bi-play-circle-fill"></i>
                    <span className="text-truncate" style={{ maxWidth: '140px' }}>{dataset.name}</span>
                  </Link>
                )}
                <span className="badge bg-secondary py-2 px-3 d-flex align-items-center gap-2">
                  <i className="bi bi-person-circle"></i>
                  <span>{user?.name || user?.email}</span>
                </span>
                <button onClick={handleLogout} className="btn btn-outline-danger btn-sm d-flex align-items-center gap-1">
                  <i className="bi bi-box-arrow-right"></i>
                  <span>Logout</span>
                </button>
              </div>
            </>
          ) : (
            <div className="navbar-nav ms-auto gap-2 align-items-lg-center">
              <a
                href="#how-it-works"
                onClick={(e) => {
                  closeMobileMenu();
                  if (window.location.pathname === '/' || window.location.pathname === '/home') {
                    e.preventDefault();
                    const el = document.getElementById('how-it-works');
                    if (el) el.scrollIntoView({ behavior: 'smooth' });
                  }
                }}
                className="nav-link text-light text-opacity-75 px-2"
              >
                How It Works
              </a>
              <a
                href="#capabilities"
                onClick={(e) => {
                  closeMobileMenu();
                  if (window.location.pathname === '/' || window.location.pathname === '/home') {
                    e.preventDefault();
                    const el = document.getElementById('capabilities');
                    if (el) el.scrollIntoView({ behavior: 'smooth' });
                  }
                }}
                className="nav-link text-light text-opacity-75 px-2"
              >
                Scientific Capabilities
              </a>
              <Link to="/login" onClick={closeMobileMenu} className="btn btn-outline-light btn-sm ms-lg-2">
                Login
              </Link>
              <Link to="/register" onClick={closeMobileMenu} className="btn btn-primary btn-sm">
                Register
              </Link>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
