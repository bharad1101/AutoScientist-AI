import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from '../pages/LoginPage';
import { authService } from '../services/authService';
import { AuthProvider } from '../context/AuthContext';

vi.mock('../services/authService', () => ({
  authService: {
    getCurrentUser: vi.fn(),
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  },
}));

const renderLoginPage = () => {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>
  );
};

describe('LoginPage Authentication & Error Diagnostics', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    authService.getCurrentUser.mockResolvedValue({ data: null });
  });

  it('1. Quick Fill demo credentials populates the email and password inputs', async () => {
    renderLoginPage();

    const quickFillBtn = screen.getByRole('button', { name: /quick fill/i });
    expect(quickFillBtn).toBeInTheDocument();

    fireEvent.click(quickFillBtn);

    expect(screen.getByLabelText(/email address/i)).toHaveValue('jane@university.edu');
    expect(screen.getByLabelText(/password/i)).toHaveValue('SecurePassword123!');
  });

  it('2. Trims leading and trailing spaces from email before sending to login service', async () => {
    authService.login.mockResolvedValue({
      data: {
        token: 'mock-jwt-token',
        user: { name: 'Researcher', email: 'jane@university.edu' },
      },
    });

    renderLoginPage();

    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: '   jane@university.edu   ' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'SecurePassword123!' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(authService.login).toHaveBeenCalledWith('jane@university.edu', 'SecurePassword123!');
    });
  });

  it('3. Displays server network error clearly when backend is unreachable', async () => {
    // Axios network error has no response object
    authService.login.mockRejectedValue(new Error('Network Error'));

    renderLoginPage();

    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: 'jane@university.edu' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'wrongPassword' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/unable to connect to autoscientist server/i)
      ).toBeInTheDocument();
    });
  });

  it('4. Displays invalid credentials message when backend returns 401 Unauthorized', async () => {
    authService.login.mockRejectedValue({
      response: {
        status: 401,
        data: {
          success: false,
          message: 'Invalid email or password.',
        },
      },
    });

    renderLoginPage();

    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: 'jane@university.edu' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'wrongPassword' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
    });
  });

  it('5. Displays validation message when backend returns 400 Bad Request with field errors', async () => {
    authService.login.mockRejectedValue({
      response: {
        status: 400,
        data: {
          success: false,
          message: 'Validation failed',
          data: {
            email: 'Valid email is required',
          },
        },
      },
    });

    renderLoginPage();

    const form = screen.getByRole('button', { name: /sign in/i }).closest('form');
    fireEvent.change(screen.getByLabelText(/email address/i), {
      target: { value: 'invalid@format.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'password123' },
    });

    fireEvent.submit(form);

    await waitFor(() => {
      expect(screen.getByText(/valid email is required/i)).toBeInTheDocument();
    });
  });
});
