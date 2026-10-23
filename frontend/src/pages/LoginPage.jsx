import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { login, isAuthenticated, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const searchParams = new URLSearchParams(location.search);
  const redirectParam = searchParams.get('redirect');
  const from = location.state?.from?.pathname
    ? location.state.from.pathname + (location.state.from.search || '')
    : redirectParam
    ? decodeURIComponent(redirectParam)
    : '/dashboard';

  useEffect(() => {
    if (!loading && isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [loading, isAuthenticated, navigate]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    const trimmedEmail = email ? email.trim() : '';

    try {
      await login(trimmedEmail, password);
      navigate(from, { replace: true });
    } catch (err) {
      console.error('Authentication attempt failed:', err);
      if (!err.response) {
        setError('Unable to connect to AutoScientist server. Please verify the backend is running.');
      } else if (err.response.status === 401) {
        setError(err.response.data?.message || 'Invalid email or password.');
      } else if (err.response.status === 400 && err.response.data?.data) {
        const fieldError = Object.values(err.response.data.data)[0];
        setError(fieldError || err.response.data?.message || 'Invalid input. Please check your credentials.');
      } else {
        setError(err.response.data?.message || err.message || 'Authentication failed. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container py-5">
      <div className="row justify-content-center">
        <div className="col-md-6 col-lg-5">
          <div className="card shadow-sm border-0 p-4">
            <div className="text-center mb-4">
              <div className="d-inline-flex p-3 rounded-circle bg-primary bg-opacity-10 text-primary mb-3">
                <i className="bi bi-shield-lock-fill fs-2"></i>
              </div>
              <h3 className="fw-bold">Sign In</h3>
              <p className="text-muted small">Access your AutoScientist research workspace</p>
            </div>

            {error && (
              <div className="alert alert-danger d-flex align-items-center gap-2" role="alert">
                <i className="bi bi-exclamation-triangle-fill"></i>
                <div className="small">{error}</div>
              </div>
            )}

            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <label className="form-label small fw-semibold" htmlFor="loginEmail">
                  Email Address
                </label>
                <div className="input-group">
                  <span className="input-group-text"><i className="bi bi-envelope"></i></span>
                  <input
                    id="loginEmail"
                    type="email"
                    className="form-control"
                    placeholder="name@institution.org"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="mb-4">
                <label className="form-label small fw-semibold" htmlFor="loginPassword">
                  Password
                </label>
                <div className="input-group">
                  <span className="input-group-text"><i className="bi bi-key"></i></span>
                  <input
                    id="loginPassword"
                    type="password"
                    className="form-control"
                    placeholder="••••••••"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                </div>
              </div>

              <button
                type="submit"
                className="btn btn-primary w-100 py-2 fw-semibold d-flex align-items-center justify-content-center gap-2"
                disabled={submitting}
              >
                {submitting ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status"></span>
                    <span>Authenticating...</span>
                  </>
                ) : (
                  <>
                    <i className="bi bi-box-arrow-in-right"></i>
                    <span>Sign In</span>
                  </>
                )}
              </button>
            </form>

            <div className="mt-3 p-3 bg-light rounded-3 border text-muted small">
              <div className="fw-semibold text-dark mb-1 d-flex align-items-center justify-content-between">
                <span><i className="bi bi-info-circle me-1"></i> Demo Credentials</span>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-primary py-0 px-2"
                  style={{ fontSize: '0.75rem' }}
                  onClick={() => {
                    setEmail('jane@university.edu');
                    setPassword('SecurePassword123!');
                  }}
                >
                  Quick Fill
                </button>
              </div>
              <div className="font-monospace text-secondary">jane@university.edu / SecurePassword123!</div>
            </div>

            <div className="text-center mt-4 pt-3 border-top">
              <span className="text-muted small">Don't have an account yet? </span>
              <Link to="/register" className="small fw-semibold text-primary text-decoration-none">
                Register here
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
