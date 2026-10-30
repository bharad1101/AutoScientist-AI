import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { authService } from '../services/authService';

const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await authService.forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      if (!err.response) {
        setError('Unable to connect to AutoScientist server. Please verify the backend is running.');
      } else if (err.response.status === 400 && err.response.data?.data) {
        const fieldError = Object.values(err.response.data.data)[0];
        setError(fieldError || err.response.data?.message || 'Invalid email address.');
      } else {
        setError(err.response?.data?.message || err.message || 'Could not request a password reset.');
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
                <i className="bi bi-envelope-check-fill fs-2"></i>
              </div>
              <h3 className="fw-bold">Forgot Password</h3>
              <p className="text-muted small">Request a single-use reset link for your account</p>
            </div>

            {error && (
              <div className="alert alert-danger d-flex align-items-center gap-2" role="alert">
                <i className="bi bi-exclamation-triangle-fill"></i>
                <div className="small">{error}</div>
              </div>
            )}

            {sent ? (
              <div className="alert alert-success" role="status">
                <div className="fw-semibold mb-1">Check your inbox</div>
                <div className="small">
                  If an account exists for <span className="fw-semibold">{email.trim()}</span>, a password
                  reset link has been sent. The link expires shortly and can be used only once.
                </div>
              </div>
            ) : (
              <form onSubmit={handleSubmit}>
                <div className="mb-4">
                  <label className="form-label small fw-semibold" htmlFor="forgotEmail">
                    Email Address
                  </label>
                  <div className="input-group">
                    <span className="input-group-text"><i className="bi bi-envelope"></i></span>
                    <input
                      id="forgotEmail"
                      type="email"
                      className="form-control"
                      placeholder="name@institution.org"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
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
                      <span>Sending...</span>
                    </>
                  ) : (
                    <>
                      <i className="bi bi-send"></i>
                      <span>Send Reset Link</span>
                    </>
                  )}
                </button>
              </form>
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

export default ForgotPasswordPage;
