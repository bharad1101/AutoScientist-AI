import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import EdaGallery from '../components/EdaGallery';
import { analysisService } from '../services/analysisService';

vi.mock('../services/analysisService', () => ({
  analysisService: {
    getHistograms: vi.fn(),
    getMissingness: vi.fn(),
    getCategoricalSummaries: vi.fn(),
    getScatter: vi.fn(),
    getTimeSeries: vi.fn(),
    getGroupDistributions: vi.fn(),
    getMissingnessMatrix: vi.fn(),
  },
}));

vi.mock('react-chartjs-2', () => ({
  Bar: (props) => <div data-testid="mock-bar-chart" data-labels={JSON.stringify(props.data?.labels)} />,
  Line: (props) => <div data-testid="mock-line-chart" data-labels={JSON.stringify(props.data?.labels)} />,
  Scatter: (props) => (
    <div
      data-testid="mock-scatter-chart"
      data-points-count={props.data?.datasets?.[0]?.data?.length}
    />
  ),
}));

describe('EdaGallery', () => {
  const sampleColumns = [
    { columnName: 'age', dataType: 'NUMERIC', isTarget: false, isExcluded: false },
    { columnName: 'bmi', dataType: 'NUMERIC', isTarget: false, isExcluded: false },
    { columnName: 'gender', dataType: 'CATEGORICAL', isTarget: false, isExcluded: false },
    { columnName: 'record_date', dataType: 'DATE_TIME', isTarget: false, isExcluded: false },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    analysisService.getHistograms.mockResolvedValue({
      data: [
        {
          variable: 'age',
          binStarts: [20, 30],
          binEnds: [30, 40],
          counts: [15, 25],
        },
      ],
    });
    analysisService.getMissingness.mockResolvedValue({
      data: [
        { variable: 'age', missingCount: 0, missingPct: 0.0 },
        { variable: 'bmi', missingCount: 5, missingPct: 5.0 },
      ],
    });
    analysisService.getCategoricalSummaries.mockResolvedValue({
      data: [
        {
          variable: 'gender',
          topCategories: [
            { category: 'Male', count: 50 },
            { category: 'Female', count: 50 },
          ],
        },
      ],
    });
    analysisService.getScatter.mockResolvedValue({
      data: {
        xVariable: 'age',
        yVariable: 'bmi',
        points: [[25, 22.1], [35, 27.4]],
        fit: { slope: 0.53, intercept: 10.2, rSquared: 0.65, pValue: 0.001 },
      },
    });
    analysisService.getTimeSeries.mockResolvedValue({
      data: [
        { timestamp: '2026-01-01', value: 24.5 },
        { timestamp: '2026-01-02', value: 25.1 },
      ],
    });
    analysisService.getGroupDistributions.mockResolvedValue({
      data: [
        {
          groupName: 'Male',
          count: 50,
          binStarts: [20],
          binEnds: [30],
          proportions: [0.5],
        },
      ],
    });
    analysisService.getMissingnessMatrix.mockResolvedValue({
      data: {
        variables: ['age', 'bmi'],
        present: [
          [true, true],
          [true, false],
        ],
        rowIndexes: [0, 1],
      },
    });
  });

  it('renders gallery with initial analytical data and charts', async () => {
    render(<EdaGallery datasetId={1} columns={sampleColumns} />);

    expect(screen.getByRole('heading', { name: /Numeric Distributions/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Bivariate Scatter/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Missingness Audit/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Missing-Value Matrix/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Cohort Distribution Comparison/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Categorical Composition/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Time Series/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(analysisService.getHistograms).toHaveBeenCalledWith(1);
      expect(analysisService.getMissingness).toHaveBeenCalledWith(1);
      expect(analysisService.getCategoricalSummaries).toHaveBeenCalledWith(1);
    });
  });

  it('renders empty fallback messages when datasetId is absent or no analytic columns exist', () => {
    render(<EdaGallery datasetId={null} columns={[]} />);

    expect(screen.getByText(/No numeric variables are available for distribution analysis/i)).toBeInTheDocument();
    expect(screen.getByText(/Select two distinct numeric variables to render a scatter plot/i)).toBeInTheDocument();
    expect(screen.getByText(/No categorical variables are available for composition analysis/i)).toBeInTheDocument();
  });

  it('handles histogram fetch error gracefully with alert banner', async () => {
    analysisService.getHistograms.mockRejectedValue({
      response: { data: { message: 'Failed to generate histograms' } },
    });

    render(<EdaGallery datasetId={1} columns={sampleColumns} />);

    await waitFor(() => {
      expect(screen.getByText('Failed to generate histograms')).toBeInTheDocument();
    });
  });

  it('renders scatter plot with linear fit metrics when scatter data is returned', async () => {
    render(<EdaGallery datasetId={1} columns={sampleColumns} />);

    await waitFor(() => {
      expect(analysisService.getScatter).toHaveBeenCalledWith(1, 'age', 'bmi');
    });

    await waitFor(() => {
      expect(screen.getByText('0.6500')).toBeInTheDocument();
      expect(screen.getByText('0.5300')).toBeInTheDocument();
      expect(screen.getByText('10.2000')).toBeInTheDocument();
    });
  });

  it('loads and displays the missingness matrix table', async () => {
    render(<EdaGallery datasetId={1} columns={sampleColumns} />);

    await waitFor(() => {
      expect(analysisService.getMissingnessMatrix).toHaveBeenCalledWith(1, 200);
      expect(screen.getByRole('heading', { name: /Missing-Value Matrix/i })).toBeInTheDocument();
    });
  });
});
