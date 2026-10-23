import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import DriftPage from '../pages/DriftPage';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';

vi.mock('../components/PrerequisiteAlert', () => ({
  default: () => <div data-testid="mock-prereq-alert" />,
}));

vi.mock('../services/datasetService', () => ({
  datasetService: {
    getDatasets: vi.fn(),
  },
}));

vi.mock('../services/analysisService', () => ({
  analysisService: {
    compareDrift: vi.fn(),
  },
}));

vi.mock('react-chartjs-2', () => ({
  Bar: (props) => (
    <div
      data-testid="mock-psi-bar-chart"
      data-labels={JSON.stringify(props.data?.labels)}
    />
  ),
  Line: (props) => (
    <div
      data-testid="mock-drift-distribution-chart"
      data-labels={JSON.stringify(props.data?.labels)}
      data-datasets-count={props.data?.datasets?.length}
    />
  ),
}));

describe('DriftPage', () => {
  const sampleDatasets = [
    { id: 1, name: 'Cohort 2025', rowCount: 1000 },
    { id: 2, name: 'Cohort 2026', rowCount: 1200 },
  ];

  const sampleDriftReport = {
    referenceDatasetId: 1,
    targetDatasetId: 2,
    overallDriftIndex: 0.1245,
    overallStatus: 'LOW_RISK',
    totalFeaturesAnalyzed: 4,
    driftedFeaturesCount: 1,
    summaryRecommendation: 'Moderate drift detected in feature age. Monitor cohort distributions.',
    columnDrifts: [
      {
        columnName: 'age',
        dataType: 'NUMERIC',
        psiScore: 0.18,
        driftStatus: 'MODERATE_DRIFT',
        ksStatistic: 0.12,
        ksPValue: 0.003,
        binEdges: [20, 30, 40, 50],
        referenceBinProportions: [0.3, 0.4, 0.3],
        targetBinProportions: [0.15, 0.5, 0.35],
      },
      {
        columnName: 'income',
        dataType: 'NUMERIC',
        psiScore: 0.04,
        driftStatus: 'STABLE',
        ksStatistic: 0.02,
        ksPValue: 0.45,
        binEdges: [10000, 50000, 100000],
        referenceBinProportions: [0.5, 0.5],
        targetBinProportions: [0.48, 0.52],
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    datasetService.getDatasets.mockResolvedValue({ data: sampleDatasets });
    analysisService.compareDrift.mockResolvedValue({ data: sampleDriftReport });
  });

  it('loads datasets and enables Analyze Drift form', async () => {
    render(<DriftPage />);

    expect(screen.getByRole('heading', { name: /Data Drift & Cohort Stability Engine/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(datasetService.getDatasets).toHaveBeenCalled();
      expect(screen.getByRole('button', { name: /Analyze Drift/i })).toBeEnabled();
    });
  });

  it('runs drift analysis and renders summary metrics, PSI chart, and distribution curves', async () => {
    render(<DriftPage />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Analyze Drift/i })).toBeEnabled();
    });

    const analyzeBtn = screen.getByRole('button', { name: /Analyze Drift/i });
    fireEvent.click(analyzeBtn);

    await waitFor(() => {
      expect(analysisService.compareDrift).toHaveBeenCalledWith(1, 2);
    });

    // Check summary statistics
    await waitFor(() => {
      expect(screen.getByText('0.1245')).toBeInTheDocument(); // Overall PSI
      expect(screen.getByText('LOW_RISK')).toBeInTheDocument(); // Status badge
      expect(screen.getByText('4')).toBeInTheDocument(); // Features analyzed
      expect(screen.getByText('1')).toBeInTheDocument(); // Drifted features
    });

    // Per-feature PSI Bar chart
    const psiChart = screen.getByTestId('mock-psi-bar-chart');
    expect(psiChart).toBeInTheDocument();
    expect(JSON.parse(psiChart.getAttribute('data-labels'))).toEqual(['age', 'income']);

    // Distribution Comparison Line chart
    const distChart = screen.getByTestId('mock-drift-distribution-chart');
    expect(distChart).toBeInTheDocument();
    expect(distChart.getAttribute('data-datasets-count')).toBe('2');

    // Recommendation banner
    expect(screen.getByText(/Moderate drift detected in feature age/i)).toBeInTheDocument();
  });

  it('shows error banner when drift evaluation fails', async () => {
    analysisService.compareDrift.mockRejectedValue({
      response: { data: { message: 'Cohorts have no overlapping columns' } },
    });

    render(<DriftPage />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Analyze Drift/i })).toBeEnabled();
    });

    const analyzeBtn = screen.getByRole('button', { name: /Analyze Drift/i });
    fireEvent.click(analyzeBtn);

    await waitFor(() => {
      expect(screen.getByText('Cohorts have no overlapping columns')).toBeInTheDocument();
    });
  });
});
