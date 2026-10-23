import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import AssociationsPage from '../pages/AssociationsPage';
import { analysisService } from '../services/analysisService';
import { datasetService } from '../services/datasetService';

vi.mock('../context/WorkflowContext', () => ({
  useWorkflow: () => ({
    datasetId: 1,
    selectDataset: vi.fn(),
    refreshWorkflowStatus: vi.fn(),
  }),
}));

vi.mock('../components/WorkflowNavigator', () => ({
  default: ({ children }) => <div data-testid="mock-workflow-navigator">{children}</div>,
}));

vi.mock('../services/analysisService', () => ({
  analysisService: {
    getAssociations: vi.fn(),
    refreshAssociations: vi.fn(),
  },
}));

vi.mock('../services/datasetService', () => ({
  datasetService: {
    getDataset: vi.fn(),
    listDatasets: vi.fn(),
  },
}));

vi.mock('react-chartjs-2', () => ({
  Bar: (props) => (
    <div
      data-testid="mock-ci-bar-chart"
      data-labels={JSON.stringify(props.data?.labels)}
      data-datasets={JSON.stringify(props.data?.datasets?.map((d) => d.label))}
    />
  ),
  Scatter: () => <div data-testid="mock-scatter-chart" />,
}));

describe('AssociationsPage', () => {
  const sampleDataset = {
    id: 1,
    name: 'Biomarker Study',
    rowCount: 500,
    columnCount: 10,
  };

  const sampleAssociations = [
    {
      id: 101,
      var1: 'age',
      var2: 'cholesterol',
      testType: 'PEARSON',
      statistic: 0.54,
      pValue: 0.0001,
      isSignificant: true,
      ciLower: 0.42,
      ciUpper: 0.64,
      ciMethod: 'Fisher z-transform 95% CI',
    },
    {
      id: 102,
      var1: 'treatment',
      var2: 'recovery_time',
      testType: 'T_TEST',
      statistic: -3.21,
      pValue: 0.002,
      isSignificant: true,
      ciLower: -4.5,
      ciUpper: -1.2,
      ciMethod: "Welch's t-test mean difference 95% CI",
    },
    {
      id: 103,
      var1: 'smoker',
      var2: 'hypertension',
      testType: 'CHI_SQUARE',
      statistic: 1.15,
      pValue: 0.28,
      isSignificant: false,
      ciUnavailableReason: "Confidence intervals are not defined for omnibus Chi-Square test (Cramer's V: 0.08)",
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    datasetService.getDataset.mockResolvedValue({ data: sampleDataset });
    datasetService.listDatasets.mockResolvedValue({ data: [sampleDataset] });
    analysisService.getAssociations.mockResolvedValue({ data: sampleAssociations });
    analysisService.refreshAssociations.mockResolvedValue({ data: sampleAssociations });
  });

  const renderComponent = (datasetId = '1') =>
    render(
      <MemoryRouter initialEntries={[`/datasets/${datasetId}/associations`]}>
        <Routes>
          <Route path="/datasets/:id/associations" element={<AssociationsPage />} />
        </Routes>
      </MemoryRouter>
    );

  it('renders dataset header and associations summary metrics', async () => {
    renderComponent('1');

    await screen.findByText(/Biomarker Study/i);
    expect(screen.getByText(/Showing/i)).toHaveTextContent('Showing 3 of 3 pairings');

    // Table rows
    expect(screen.getByText('age')).toBeInTheDocument();
    expect(screen.getByText('cholesterol')).toBeInTheDocument();
    expect(screen.getByText('treatment')).toBeInTheDocument();
  });

  it('renders Confidence Interval Explorer chart with forest-plot intervals', async () => {
    renderComponent('1');

    await screen.findByText('Confidence Interval Explorer');
    const chart = screen.getByTestId('mock-ci-bar-chart');
    expect(chart).toBeInTheDocument();

    const labels = JSON.parse(chart.getAttribute('data-labels'));
    expect(labels).toContain('age ↔ cholesterol');

    // Method note is displayed
    expect(screen.getByText(/Fisher z-transform 95% CI/i)).toBeInTheDocument();
  });

  it('filters associations when Significant Only checkbox is toggled', async () => {
    renderComponent('1');

    await screen.findByText('Biomarker Study');
    expect(screen.getByText('smoker')).toBeInTheDocument();

    const sigFilter = screen.getByRole('checkbox', { name: /Significant Only \(p < 0\.05\)/i });
    fireEvent.click(sigFilter);

    // Non-significant association 'smoker' should be excluded from view
    expect(screen.queryByText('smoker')).not.toBeInTheDocument();
    expect(screen.getByText('age')).toBeInTheDocument();
  });

  it('refreshes associations when Refresh button is clicked', async () => {
    renderComponent('1');

    await screen.findByText('Biomarker Study');
    const refreshBtn = screen.getByRole('button', { name: /Re-run Association Engine/i });
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(analysisService.refreshAssociations).toHaveBeenCalledWith('1');
    });
  });

  it('handles error state when association refresh fails', async () => {
    analysisService.refreshAssociations.mockRejectedValue({
      response: { data: { message: 'Recomputation failed on server' } },
    });
    renderComponent('1');

    await screen.findByText('Biomarker Study');
    const refreshBtn = screen.getByRole('button', { name: /Re-run Association Engine/i });
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(screen.getByText('Recomputation failed on server')).toBeInTheDocument();
    });
  });

  it('handles initial load API error gracefully and allows retry without blank screen', async () => {
    analysisService.getAssociations.mockRejectedValueOnce(
      new Error('Failed to reach associations service')
    );
    renderComponent('1');

    await waitFor(() => {
      expect(screen.getByTestId('associations-error-alert')).toBeInTheDocument();
      expect(
        screen.getByText(/Failed to reach associations service/i)
      ).toBeInTheDocument();
    });

    // Page must NOT be blank
    expect(screen.getByText(/Statistical Associations Engine/i)).toBeInTheDocument();

    // Clicking retry fetches again
    analysisService.getAssociations.mockResolvedValueOnce({ data: sampleAssociations });
    const retryBtn = screen.getByTestId('associations-retry-btn');
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText('age')).toBeInTheDocument();
      expect(screen.queryByTestId('associations-error-alert')).not.toBeInTheDocument();
    });
  });

  it('renders a distinct informative empty state when a dataset has zero associations', async () => {
    analysisService.getAssociations.mockResolvedValue({ data: [] });
    renderComponent('1');

    await waitFor(() => {
      expect(screen.getByTestId('associations-zero-pairs')).toBeInTheDocument();
    });

    expect(
      screen.getByText(/No Statistical Associations Discovered/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/No eligible pairs of variables met the criteria/i)).toBeInTheDocument();
  });

  it('renders a filter-empty state with reset button when filter matches zero pairings', async () => {
    renderComponent('1');

    await screen.findByText('Biomarker Study');
    // Filter to ANOVA (none of our sample associations are ANOVA)
    const select = screen.getByLabelText(/Filter by statistical test architecture/i);
    fireEvent.change(select, { target: { value: 'ONE_WAY_ANOVA' } });

    await waitFor(() => {
      expect(screen.getByTestId('associations-no-filter-match')).toBeInTheDocument();
    });

    // Reset button restores associations
    const resetBtn = screen.getByTestId('reset-filters-btn');
    fireEvent.click(resetBtn);

    await waitFor(() => {
      expect(screen.getByText('age')).toBeInTheDocument();
    });
  });

  it('clicking Investigate navigates to research questions without runtime exception', async () => {
    renderComponent('1');

    await screen.findByText('Biomarker Study');
    const investigateBtn = screen.getByTestId('investigate-age-cholesterol');
    expect(investigateBtn).toBeInTheDocument();

    // Clicking should execute without throwing uncaught ReferenceError: navigate is not defined
    expect(() => fireEvent.click(investigateBtn)).not.toThrow();
  });
});
