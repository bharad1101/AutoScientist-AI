import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ForgotPasswordPage from '../pages/ForgotPasswordPage';
import ResetPasswordPage from '../pages/ResetPasswordPage';
import { authService } from '../services/authService';

vi.mock('../services/authService', () => ({
  authService: {
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
  },
}));

const renderPage = (page, path) =>
  render(<MemoryRouter initialEntries={[path]}>{page}</MemoryRouter>);

describe('Password reset pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('forgot password submits a trimmed email and shows a non-enumerating confirmation', async () => {
    authService.forgotPassword.mockResolvedValue({ success: true });

    renderPage(<ForgotPasswordPage />, '/forgot-password');

    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: '  jane@university.edu  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    await waitFor(() => {
      expect(authService.forgotPassword).toHaveBeenCalledWith('jane@university.edu');
    });
    expect(screen.getByText(/check your inbox/i)).toBeInTheDocument();
    expect(screen.getByText(/if an account exists/i)).toBeInTheDocument();
  });

  it('forgot password reports a backend connection failure', async () => {
    authService.forgotPassword.mockRejectedValue(new Error('Network Error'));

    renderPage(<ForgotPasswordPage />, '/forgot-password');

    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: 'jane@university.edu' },
    });
    fireEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    await waitFor(() => {
      expect(screen.getByText(/unable to connect to autoscientist server/i)).toBeInTheDocument();
    });
  });

  it('reset password rejects mismatched passwords without calling the API', async () => {
    renderPage(<ResetPasswordPage />, '/reset-password?token=valid-token');

    fireEvent.change(screen.getByLabelText(/^new password$/i), {
      target: { value: 'NewPassword123!' },
    });
    fireEvent.change(screen.getByLabelText(/confirm new password/i), {
      target: { value: 'DifferentPassword123!' },
    });
    fireEvent.click(screen.getByRole('button', { name: /reset password/i }));

    await waitFor(() => {
      expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    });
    expect(authService.resetPassword).not.toHaveBeenCalled();
  });

  it('reset password submits the token and new password', async () => {
    authService.resetPassword.mockResolvedValue({ success: true });

    renderPage(<ResetPasswordPage />, '/reset-password?token=valid-token');

    fireEvent.change(screen.getByLabelText(/^new password$/i), {
      target: { value: 'NewPassword123!' },
    });
    fireEvent.change(screen.getByLabelText(/confirm new password/i), {
      target: { value: 'NewPassword123!' },
    });
    fireEvent.click(screen.getByRole('button', { name: /reset password/i }));

    await waitFor(() => {
      expect(authService.resetPassword).toHaveBeenCalledWith('valid-token', 'NewPassword123!');
    });
    expect(screen.getByText(/password updated/i)).toBeInTheDocument();
  });

  it('reset password surfaces an expired or invalid token message', async () => {
    authService.resetPassword.mockRejectedValue({
      response: {
        status: 400,
        data: {
          success: false,
          message: 'This password reset link is invalid or has expired. Please request a new one.',
        },
      },
    });

    renderPage(<ResetPasswordPage />, '/reset-password?token=expired-token');

    fireEvent.change(screen.getByLabelText(/^new password$/i), {
      target: { value: 'NewPassword123!' },
    });
    fireEvent.change(screen.getByLabelText(/confirm new password/i), {
      target: { value: 'NewPassword123!' },
    });
    fireEvent.click(screen.getByRole('button', { name: /reset password/i }));

    await waitFor(() => {
      expect(screen.getByText(/invalid or has expired/i)).toBeInTheDocument();
    });
  });

  it('reset password without a token shows an actionable incomplete-link state', () => {
    renderPage(<ResetPasswordPage />, '/reset-password');

    expect(screen.getByText(/this reset link is incomplete/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /reset password/i })).not.toBeInTheDocument();
  });
});
