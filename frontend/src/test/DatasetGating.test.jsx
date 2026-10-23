import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import RequireDatasetRoute from '../components/RequireDatasetRoute';
import Sidebar from '../components/Sidebar';
import Navbar from '../components/Navbar';
import * as AuthContextModule from '../context/AuthContext';
import * as WorkflowContextModule from '../context/WorkflowContext';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

vi.mock('../context/WorkflowContext', () => ({
  useWorkflow: vi.fn(),
}));

describe('Dataset-First Access Control (Requirement 2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('RequireDatasetRoute Guard', () => {
    it('renders a loading spinner while checking dataset access (avoiding flash)', () => {
      AuthContextModule.useAuth.mockReturnValue({
        isAuthenticated: true,
        loading: false,
      });
      WorkflowContextModule.useWorkflow.mockReturnValue({
        hasDatasets: false,
        datasetsLoading: true,
      });

      render(
        <MemoryRouter>
          <RequireDatasetRoute>
            <div data-testid="protected-content">Secret Analysis View</div>
          </RequireDatasetRoute>
        </MemoryRouter>
      );

      expect(screen.getByTestId('dataset-guard-loading')).toBeInTheDocument();
      expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
      expect(screen.getByText(/checking dataset access/i)).toBeInTheDocument();
    });

    it('blocks access and renders informative locked screen with CTAs when no dataset exists', () => {
      AuthContextModule.useAuth.mockReturnValue({
        isAuthenticated: true,
        loading: false,
      });
      WorkflowContextModule.useWorkflow.mockReturnValue({
        hasDatasets: false,
        datasetsLoading: false,
      });

      render(
        <MemoryRouter>
          <RequireDatasetRoute>
            <div data-testid="protected-content">Secret Analysis View</div>
          </RequireDatasetRoute>
        </MemoryRouter>
      );

      // Protected content must NOT be displayed
      expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();

      // Informative locked state card
      expect(screen.getByTestId('dataset-guard-locked')).toBeInTheDocument();
      expect(screen.getByText(/analysis workspace locked/i)).toBeInTheDocument();
      expect(
        screen.getByText(/upload a dataset to unlock scientific analysis\./i)
      ).toBeInTheDocument();

      // Working CTAs
      const uploadBtn = screen.getByRole('link', { name: /upload dataset/i });
      expect(uploadBtn).toBeInTheDocument();
      expect(uploadBtn.getAttribute('href')).toBe('/datasets');

      const dashboardBtn = screen.getByRole('link', { name: /go to dashboard/i });
      expect(dashboardBtn).toBeInTheDocument();
      expect(dashboardBtn.getAttribute('href')).toBe('/dashboard');
    });

    it('renders the analysis workspace when at least one dataset exists', () => {
      AuthContextModule.useAuth.mockReturnValue({
        isAuthenticated: true,
        loading: false,
      });
      WorkflowContextModule.useWorkflow.mockReturnValue({
        hasDatasets: true,
        datasetsLoading: false,
      });

      render(
        <MemoryRouter>
          <RequireDatasetRoute>
            <div data-testid="protected-content">Secret Analysis View</div>
          </RequireDatasetRoute>
        </MemoryRouter>
      );

      expect(screen.getByTestId('protected-content')).toBeInTheDocument();
      expect(screen.queryByTestId('dataset-guard-locked')).not.toBeInTheDocument();
    });
  });

  describe('Sidebar Navigation Gating', () => {
    it('disables analysis items with lock badges and shows upload CTA card when hasDatasets is false', () => {
      WorkflowContextModule.useWorkflow.mockReturnValue({
        dataset: null,
        datasetId: null,
        hasDatasets: false,
        datasetsLoading: false,
      });

      render(
        <MemoryRouter>
          <Sidebar />
        </MemoryRouter>
      );

      // Dashboard and Datasets links remain clickable and open
      expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /datasets/i })).toBeInTheDocument();

      // Analysis items are locked spans with is-disabled class and aria-disabled="true"
      const lockedAssociations = screen.getByTestId('sidebar-locked-associations');
      expect(lockedAssociations).toHaveClass('is-disabled');
      expect(lockedAssociations).toHaveAttribute('aria-disabled', 'true');
      expect(lockedAssociations).toHaveAttribute(
        'title',
        'Upload a dataset to unlock scientific analysis.'
      );

      const lockedAutoml = screen.getByTestId('sidebar-locked-automl---xai');
      expect(lockedAutoml).toHaveClass('is-disabled');
      expect(lockedAutoml).toHaveAttribute('aria-disabled', 'true');

      // The sidebar prompt card with Upload Dataset CTA is visible
      expect(screen.getByTestId('sidebar-upload-cta-card')).toBeInTheDocument();
      expect(
        screen.getByText(/upload a dataset to unlock scientific analysis\./i)
      ).toBeInTheDocument();
    });

    it('enables all analysis items when hasDatasets is true', () => {
      WorkflowContextModule.useWorkflow.mockReturnValue({
        dataset: { id: 10, name: 'Clinical Trial Cohort' },
        datasetId: 10,
        hasDatasets: true,
        datasetsLoading: false,
      });

      render(
        <MemoryRouter>
          <Sidebar />
        </MemoryRouter>
      );

      // Analysis items are regular links
      expect(screen.getByRole('link', { name: /associations/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /automl & xai/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /findings/i })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /reports/i })).toBeInTheDocument();

      // Upload CTA card should NOT be shown
      expect(screen.queryByTestId('sidebar-upload-cta-card')).not.toBeInTheDocument();

      // Footer displays active dataset name
      expect(screen.getByText('Clinical Trial Cohort')).toBeInTheDocument();
    });
  });

  describe('Navbar Mobile Gating', () => {
    it('shows lock indicators on mobile analysis items when hasDatasets is false', () => {
      AuthContextModule.useAuth.mockReturnValue({
        user: { name: 'Test Scientist', email: 'test@scientist.org' },
        isAuthenticated: true,
        logout: vi.fn(),
      });
      WorkflowContextModule.useWorkflow.mockReturnValue({
        dataset: null,
        hasDatasets: false,
        datasetsLoading: false,
      });

      render(
        <MemoryRouter>
          <Navbar />
        </MemoryRouter>
      );

      // In the mobile menu, check for the upload CTA button
      expect(
        screen.getByRole('link', { name: /upload dataset to unlock/i })
      ).toBeInTheDocument();

      // Gated items have aria-disabled in mobile view
      const lockedSpans = screen.getAllByTitle('Upload a dataset to unlock scientific analysis.');
      expect(lockedSpans.length).toBeGreaterThan(0);
      expect(lockedSpans[0]).toHaveAttribute('aria-disabled', 'true');
    });
  });
});
