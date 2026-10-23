import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DashboardPage from '../pages/DashboardPage';
import { analysisService } from '../services/analysisService';

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { name: 'Dr. Jane Doe' } }),
}));

vi.mock('../services/analysisService', () => ({
  analysisService: {
    getDashboardStats: vi.fn(),
  },
}));

vi.mock('react-chartjs-2', () => ({
  Bar: (props) => (
    <div
      data-testid="mock-activity-bar-chart"
      data-labels={JSON.stringify(props.data?.labels)}
      data-datasets-count={props.data?.datasets?.length}
    />
  ),
}));

describe('DashboardPage', () => {
  const sampleStats = {
    totalDatasets: 5,
    totalObservations: 12500,
    totalAssociations: 42,
    totalModelsTrained: 18,
    averageQualityScore: 88,
    recentActivity: [
      {
        eventType: 'DATASET_UPLOAD',
        title: 'Uploaded Genomics Study',
        description: 'Profiled 5,000 rows across 25 features',
        datasetName: 'Genomics Study',
        timestamp: '2026-03-01T12:00:00Z',
      },
      {
        eventType: 'FINDING_PRODUCED',
        title: 'Discovered Biomarker Association',
        description: 'Significant Welch t-test p < 0.001',
        datasetName: 'Clinical Cohort',
        timestamp: '2026-03-01T14:30:00Z',
      },
    ],
    activity: {
      startDate: '2026-02-01',
      endDate: '2026-03-01',
      totalEvents: 24,
      lifetimeEvents: 30,
      truncated: true,
      buckets: [
        {
          date: '2026-02-01',
          total: 4,
          datasetUploads: 1,
          experiments: 2,
          findings: 1,
          questions: 0,
          reports: 0,
        },
        {
          date: '2026-02-02',
          total: 3,
          datasetUploads: 0,
          experiments: 1,
          findings: 1,
          questions: 1,
          reports: 0,
        },
      ],
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderPage = () =>
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );

  it('renders welcome banner and metric cards with stats from API', async () => {
    analysisService.getDashboardStats.mockResolvedValue({ data: sampleStats });
    renderPage();

    expect(screen.getByText(/Welcome back, Dr. Jane Doe!/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('5')).toBeInTheDocument(); // totalDatasets
      expect(screen.getByText(/12,500 rows profiled/i)).toBeInTheDocument(); // totalObservations
      expect(screen.getByText('42')).toBeInTheDocument(); // totalAssociations
      expect(screen.getByText('18')).toBeInTheDocument(); // totalModelsTrained
      expect(screen.getByText(/88 \/ 100/i)).toBeInTheDocument(); // averageQualityScore
    });
  });

  it('renders research activity stacked histogram with 5 event series when events exist', async () => {
    analysisService.getDashboardStats.mockResolvedValue({ data: sampleStats });
    renderPage();

    await waitFor(() => {
      const chart = screen.getByTestId('mock-activity-bar-chart');
      expect(chart).toBeInTheDocument();
      expect(chart.getAttribute('data-datasets-count')).toBe('5');
    });

    expect(screen.getByText(/24 event\(s\) in window/i)).toBeInTheDocument();
    expect(screen.getByText(/\(most recent 60 days\)/i)).toBeInTheDocument();
  });

  it('renders empty fallback when activity has no events recorded', async () => {
    const emptyActivityStats = {
      ...sampleStats,
      activity: {
        startDate: '2026-02-01',
        endDate: '2026-03-01',
        totalEvents: 0,
        lifetimeEvents: 0,
        truncated: false,
        buckets: [{ date: '2026-02-01', total: 0 }],
      },
    };
    analysisService.getDashboardStats.mockResolvedValue({ data: emptyActivityStats });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/No research activity has been recorded yet/i)).toBeInTheDocument();
    });
  });

  it('renders recent datasets list in the provenance card', async () => {
    analysisService.getDashboardStats.mockResolvedValue({ data: sampleStats });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Genomics Study')).toBeInTheDocument();
      expect(screen.getByText('Clinical Cohort')).toBeInTheDocument();
    });
  });
});
