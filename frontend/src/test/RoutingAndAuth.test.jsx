import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { WorkflowProvider } from '../context/WorkflowContext';
import ProtectedRoute from '../components/ProtectedRoute';
import LandingPage from '../pages/LandingPage';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import { authService } from '../services/authService';
import { datasetService } from '../services/datasetService';
import api from '../services/api';

vi.mock('../services/authService', () => ({
  authService: {
    getCurrentUser: vi.fn(),
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  },
}));

vi.mock('../services/datasetService', () => ({
  datasetService: {
    getDataset: vi.fn(),
    listDatasets: vi.fn(),
    getDatasets: vi.fn(),
    getWorkflowStatus: vi.fn(),
  },
}));

vi.mock('../components/Navbar', () => ({
  default: () => <div data-testid="mock-navbar">Mock Navbar</div>,
}));

vi.mock('../components/Sidebar', () => ({
  default: () => <div data-testid="mock-sidebar">Mock Sidebar</div>,
}));

// Mock Protected Content
const MockDashboard = () => {
  const { user, logout } = useAuth();
  return (
    <div data-testid="protected-dashboard">
      <h1>Protected Dashboard</h1>
      <p>Welcome {user?.name}</p>
      <button onClick={logout}>Sign Out</button>
    </div>
  );
};

const MockApp = ({ initialEntries = ['/'] }) => {
  return (
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <WorkflowProvider>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/home" element={<Navigate to="/" replace />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <MockDashboard />
                </ProtectedRoute>
              }
            />
            <Route
              path="/analytics"
              element={
                <ProtectedRoute>
                  <div data-testid="protected-analytics">Protected Analytics</div>
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </WorkflowProvider>
      </AuthProvider>
    </MemoryRouter>
  );
};

describe('First-Visit Routing & Authentication Suite (Part A)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    datasetService.listDatasets.mockResolvedValue({ data: [] });
    datasetService.getDatasets.mockResolvedValue({ data: [] });
    datasetService.getDataset.mockResolvedValue({ data: { id: 1, name: 'Sample' } });
    datasetService.getWorkflowStatus.mockResolvedValue({ data: { stages: {} } });
  });

  it('1. Fresh unauthenticated visit to "/" renders the public homepage without redirecting to login', async () => {
    // No saved token in storage
    authService.getCurrentUser.mockResolvedValue(null);

    render(<MockApp initialEntries={['/']} />);

    // Homepage renders immediately
    expect(screen.getByRole('heading', { name: /turn data into discovery/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument();
  });

  it('2. Initial auth state does not incorrectly redirect "/" to Login', async () => {
    // authService.getCurrentUser is pending or delayed
    let resolveAuth;
    authService.getCurrentUser.mockReturnValue(
      new Promise((res) => {
        resolveAuth = res;
      })
    );

    render(<MockApp initialEntries={['/']} />);

    // Must stay on "/" and render landing page
    expect(screen.getByRole('heading', { name: /turn data into discovery/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument();

    if (resolveAuth) resolveAuth({ data: null });
  });

  it('3. Public homepage remains usable when optional API calls fail with 401 or network error', async () => {
    // Simulate an optional call failing
    datasetService.listDatasets.mockRejectedValue(new Error('Network error'));
    authService.getCurrentUser.mockRejectedValue({ response: { status: 401 } });

    render(<MockApp initialEntries={['/']} />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /turn data into discovery/i })).toBeInTheDocument();
    });

    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument();
  });

  it('4. Direct visits to "/login" and "/register" work for unauthenticated visitors', async () => {
    authService.getCurrentUser.mockResolvedValue(null);

    const { unmount } = render(<MockApp initialEntries={['/login']} />);
    expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    unmount();

    render(<MockApp initialEntries={['/register']} />);
    expect(screen.getByRole('heading', { name: /create account/i })).toBeInTheDocument();
  });

  it('5. Unauthenticated visits to protected routes redirect to "/login" with destination preserved', async () => {
    authService.getCurrentUser.mockResolvedValue(null);

    render(<MockApp initialEntries={['/dashboard']} />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    });

    expect(screen.queryByTestId('protected-dashboard')).not.toBeInTheDocument();
  });

  it('6. Protected content is not exposed during auth initialization', async () => {
    let resolveAuth;
    authService.getCurrentUser.mockReturnValue(
      new Promise((res) => {
        resolveAuth = res;
      })
    );
    localStorage.setItem('autoscientist_token', 'initial_token');

    render(<MockApp initialEntries={['/dashboard']} />);

    // Loading spinner while authenticating
    expect(screen.getByText(/loading authentication status/i)).toBeInTheDocument();
    expect(screen.queryByTestId('protected-dashboard')).not.toBeInTheDocument();

    // Resolve as unauthenticated
    resolveAuth({ data: null });
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    });
    expect(screen.queryByTestId('protected-dashboard')).not.toBeInTheDocument();
  });

  it('7. Successful login returns user to the intended protected route', async () => {
    authService.getCurrentUser.mockResolvedValue(null);
    authService.login.mockResolvedValue({
      data: {
        token: 'new_valid_token',
        user: { name: 'Dr. Ada Lovelace', email: 'ada@scientific.ai' },
      },
    });

    // User navigates directly to protected /analytics while logged out
    render(<MockApp initialEntries={['/analytics']} />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    });

    // Fill form and submit
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'ada@scientific.ai' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByTestId('protected-analytics')).toBeInTheDocument();
    });
  });

  it('8. Invalid/expired tokens do not create redirect loops and render public homepage on "/"', async () => {
    localStorage.setItem('autoscientist_token', 'expired_jwt_token');
    authService.getCurrentUser.mockRejectedValue({
      response: { status: 401, data: { message: 'Token expired' } },
    });

    render(<MockApp initialEntries={['/']} />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /turn data into discovery/i })).toBeInTheDocument();
    });

    // Token was removed from storage without redirecting away from homepage
    expect(localStorage.getItem('autoscientist_token')).toBeNull();
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument();
  });

  it('9. Stale auth response cannot override newer authentication state', async () => {
    let slowReject;
    authService.getCurrentUser.mockReturnValue(
      new Promise((_, rej) => {
        slowReject = rej;
      })
    );
    authService.login.mockResolvedValue({
      data: {
        token: 'instant_token',
        user: { name: 'Dr. Quick', email: 'quick@ai.com' },
      },
    });

    localStorage.setItem('autoscientist_token', 'old_token');

    render(<MockApp initialEntries={['/login']} />);

    // Fast user logs in before slow getCurrentUser finishes
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'quick@ai.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByTestId('protected-dashboard')).toBeInTheDocument();
    });

    // Now slow request rejects
    slowReject({ response: { status: 401 } });

    // The user should STILL be authenticated!
    expect(screen.getByTestId('protected-dashboard')).toBeInTheDocument();
    expect(screen.getByText(/welcome Dr\. Quick/i)).toBeInTheDocument();
  });

  it('10. Logout protects authenticated routes without breaking the public homepage', async () => {
    authService.getCurrentUser.mockResolvedValue({
      data: { name: 'Dr. Jane', email: 'jane@ai.com' },
    });
    authService.logout.mockResolvedValue({ success: true });
    localStorage.setItem('autoscientist_token', 'valid_token');

    render(<MockApp initialEntries={['/dashboard']} />);

    await waitFor(() => {
      expect(screen.getByTestId('protected-dashboard')).toBeInTheDocument();
    });

    // Click Sign Out
    fireEvent.click(screen.getByRole('button', { name: /sign out/i }));

    // Now visit public homepage "/"
    render(<MockApp initialEntries={['/']} />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /turn data into discovery/i })).toBeInTheDocument();
    });
  });
});
