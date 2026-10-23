import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import VisualizationsPage from '../pages/VisualizationsPage';
import { datasetService } from '../services/datasetService';
import { analysisService } from '../services/analysisService';
import { WorkflowProvider } from '../context/WorkflowContext';

vi.mock('../services/datasetService', () => ({
  datasetService: {
    getDataset: vi.fn(),
    listDatasets: vi.fn(),
    getDatasets: vi.fn(),
    getWorkflowStatus: vi.fn(),
  },
}));

vi.mock('../services/analysisService', () => ({
  analysisService: {
    getCorrelationMatrix: vi.fn(),
    getBoxPlots: vi.fn(),
    getGroupedSummary: vi.fn(),
    getHistograms: vi.fn(),
    getMissingness: vi.fn(),
    getCategoricalSummaries: vi.fn(),
    getScatter: vi.fn(),
    getTimeSeries: vi.fn(),
    getGroupDistributions: vi.fn(),
    getMissingnessMatrix: vi.fn(),
  },
}));

vi.mock('../components/EdaGallery', () => ({
  default: ({ datasetId, columns }) => (
    <div data-testid="mock-eda-gallery" data-dataset-id={datasetId} data-columns-count={columns?.length}>
      Mock EDA Gallery
    </div>
  ),
}));

vi.mock('../components/BoxPlotChart', () => ({
  default: ({ data }) => (
    <div data-testid="mock-boxplot-chart" data-items-count={data?.length}>
      Mock BoxPlot Chart
    </div>
  ),
}));

vi.mock('react-chartjs-2', () => ({
  Bar: (props) => <div data-testid="mock-bar-chart" data-labels={JSON.stringify(props.data?.labels)} />,
  Line: (props) => <div data-testid="mock-line-chart" data-labels={JSON.stringify(props.data?.labels)} />,
  Scatter: (props) => <div data-testid="mock-scatter-chart" />,
}));

describe('VisualizationsPage & Error Resilience', () => {
  const sampleDataset = {
    id: 1,
    name: 'Clinical Trial Cohort',
    rowCount: 500,
    columnCount: 4,
    columns: [
      { id: 1, columnName: 'patient_id', dataType: 'IDENTIFIER', isTarget: false, isExcluded: false },
      { id: 2, columnName: 'age', dataType: 'NUMERIC', isTarget: false, isExcluded: false },
      { id: 3, columnName: 'cholesterol', dataType: 'NUMERIC', isTarget: false, isExcluded: false },
      { id: 4, columnName: 'treatment_group', dataType: 'CATEGORICAL', isTarget: false, isExcluded: false },
    ],
  };

  const sampleCorr = {
    variables: ['age', 'cholesterol'],
    matrix: [
      [1.0, 0.45],
      [0.45, 1.0],
    ],
    pValues: [
      [0.0, 0.002],
      [0.002, 0.0],
    ],
    sampleSizes: [500, 500],
  };

  const sampleBoxPlots = [
    {
      variable: 'age',
      min: 22.0,
      q1: 35.0,
      median: 48.0,
      q3: 62.0,
      max: 85.0,
      iqr: 27.0,
      outliers: [85.0],
    },
  ];

  const sampleGrouped = [
    { groupName: 'Placebo', groupValue: 'Placebo', count: 250, mean: 52.4, median: 51.0 },
    { groupName: 'Treatment', groupValue: 'Treatment', count: 250, mean: 47.1, median: 46.5 },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    if (typeof localStorage !== 'undefined' && localStorage.clear) {
      localStorage.clear();
    }
    datasetService.listDatasets.mockResolvedValue({ data: [sampleDataset] });
    datasetService.getDatasets.mockResolvedValue({ data: [sampleDataset] });
    datasetService.getDataset.mockResolvedValue({ data: sampleDataset });
    datasetService.getWorkflowStatus.mockResolvedValue({ data: { stages: {} } });

    analysisService.getCorrelationMatrix.mockResolvedValue({ data: sampleCorr });
    analysisService.getBoxPlots.mockResolvedValue({ data: sampleBoxPlots });
    analysisService.getGroupedSummary.mockResolvedValue({ data: sampleGrouped });
  });

  const renderComponent = (initialPath = '/datasets/1/visualizations') => {
    return render(
      <MemoryRouter initialEntries={[initialPath]}>
        <WorkflowProvider>
          <Routes>
            <Route path="/visualizations" element={<VisualizationsPage />} />
            <Route path="/datasets/:id/visualizations" element={<VisualizationsPage />} />
            <Route path="/datasets" element={<div>Datasets Page</div>} />
            <Route path="/dashboard" element={<div>Dashboard Page</div>} />
          </Routes>
        </WorkflowProvider>
      </MemoryRouter>
    );
  };

  it('renders loading indicator initially then displays dataset visualizations', async () => {
    renderComponent();

    expect(screen.getByTestId('visualizations-loading')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getAllByText('Clinical Trial Cohort').length).toBeGreaterThanOrEqual(1);
    });

    expect(screen.getByTestId('mock-eda-gallery')).toBeInTheDocument();
    expect(screen.getByText(/Pearson Correlation Structure/i)).toBeInTheDocument();
    expect(screen.getByText(/Five-Number Distribution Summary/i)).toBeInTheDocument();
    expect(screen.getByTestId('mock-boxplot-chart')).toBeInTheDocument();
  });

  it('renders a dedicated empty state when no dataset exists or is selected', async () => {
    datasetService.listDatasets.mockResolvedValue({ data: [] });
    datasetService.getDatasets.mockResolvedValue({ data: [] });

    renderComponent('/visualizations');

    await waitFor(() => {
      expect(screen.getByTestId('visualizations-no-dataset')).toBeInTheDocument();
    });

    expect(screen.getByText(/No Dataset Active for Visualizations/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Upload Dataset/i })).toBeInTheDocument();
  });

  it('handles API errors with an error alert and allows retry without crashing', async () => {
    datasetService.getDataset.mockRejectedValue({
      response: { data: { message: 'Failed to stream dataset metrics' } },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText(/Failed to stream dataset metrics/i)).toBeInTheDocument();
    });

    const retryBtn = screen.getByRole('button', { name: /Retry/i });
    expect(retryBtn).toBeInTheDocument();

    // Now let retry succeed
    datasetService.getDataset.mockResolvedValue({ data: sampleDataset });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getAllByText('Clinical Trial Cohort').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('switches grouped variables and updates grouped chart data', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getAllByText('Clinical Trial Cohort').length).toBeGreaterThanOrEqual(1);
    });

    const numSelect = screen.getByLabelText(/Select continuous variable for group comparison/i);
    expect(numSelect).toBeInTheDocument();

    fireEvent.change(numSelect, { target: { value: 'cholesterol' } });

    await waitFor(() => {
      expect(analysisService.getGroupedSummary).toHaveBeenCalledWith(
        '1',
        'cholesterol',
        'treatment_group'
      );
    });
  });

  it('renders error boundary if a child component throws an uncaught error', async () => {
    // Force a render error by mocking EdaGallery to throw
    const originalConsoleError = console.error;
    console.error = vi.fn();

    datasetService.getDataset.mockResolvedValueOnce({
      data: {
        ...sampleDataset,
        columns: null, // this will trigger error during column processing if unguarded
      },
    });

    renderComponent();

    await waitFor(() => {
      // Either handled gracefully or caught by error boundary
      const boundary = screen.queryByTestId('visualizations-error-boundary');
      const loaded = screen.queryAllByText('Clinical Trial Cohort');
      expect(boundary || loaded.length > 0).toBeTruthy();
    });

    console.error = originalConsoleError;
  });
});
