import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { authService } from '../services/authService';

const ResetPasswordPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const token = new URLSearchParams(location.search).get('token') || '';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }

    setSubmitting(true);
    try {
      await authService.resetPassword(token, newPassword);
      setSuccess(true);
      setTimeout(() => navigate('/login', { replace: true }), 2200);
    } catch (err) {
      if (!err.response) {
        setError('Unable to connect to AutoScientist server. Please verify the backend is running.');
      } else if (err.response.status === 400 && err.response.data?.data) {
        const fieldError = Object.values(err.response.data.data)[0];
        setError(fieldError || err.response.data?.message || 'Could not reset the password.');
      } else {
        setError(err.response?.data?.message || err.message || 'Could not reset the password.');
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
              <h3 className="fw-bold">Choose a New Password</h3>
              <p className="text-muted small">Your reset link can be used once</p>
            </div>

            {!token && (
              <div className="alert alert-danger" role="alert">
                <div className="fw-semibold mb-1">This reset link is incomplete</div>
                <div className="small">
                  Request a new link from the{' '}
                  <Link to="/forgot-password" className="alert-link">forgot password page</Link>.
                </div>
              </div>
            )}

            {error && (
              <div className="alert alert-danger d-flex align-items-center gap-2" role="alert">
                <i className="bi bi-exclamation-triangle-fill"></i>
                <div className="small">{error}</div>
              </div>
            )}

            {success ? (
              <div className="alert alert-success" role="status">
                <div className="fw-semibold mb-1">Password updated</div>
                <div className="small">Redirecting you to sign in...</div>
              </div>
            ) : (
              token && (
                <form onSubmit={handleSubmit}>
                  <div className="mb-3">
                    <label className="form-label small fw-semibold" htmlFor="newPassword">
                      New Password
                    </label>
                    <div className="input-group">
                      <span className="input-group-text"><i className="bi bi-key"></i></span>
                      <input
                        id="newPassword"
                        type="password"
                        className="form-control"
                        placeholder="At least 8 characters"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        required
                      />
                    </div>
                  </div>

                  <div className="mb-4">
                    <label className="form-label small fw-semibold" htmlFor="confirmNewPassword">
                      Confirm New Password
                    </label>
                    <div className="input-group">
                      <span className="input-group-text"><i className="bi bi-check2-circle"></i></span>
                      <input
                        id="confirmNewPassword"
                        type="password"
                        className="form-control"
                        placeholder="Repeat new password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
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
                        <span>Updating...</span>
                      </>
                    ) : (
                      <>
                        <i className="bi bi-check2-circle"></i>
                        <span>Reset Password</span>
                      </>
                    )}
                  </button>
                </form>
              )
            )}

            <div className="text-center mt-4 pt-3 border-top">
              <Link to="/login" className="small fw-semibold text-primary text-decoration-none">
                Back to sign in
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ResetPasswordPage;
