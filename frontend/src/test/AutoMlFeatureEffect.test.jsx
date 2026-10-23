import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AutoMlPage from '../pages/AutoMlPage';
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
    getLatestAutoMl: vi.fn(),
    trainAutoMl: vi.fn(),
    getWhatIfSchema: vi.fn(),
    simulateWhatIf: vi.fn(),
    getFeatureEffect: vi.fn(),
  },
}));

vi.mock('react-chartjs-2', () => ({
  Bar: (props) => <div data-testid="mock-bar-chart" data-labels={JSON.stringify(props.data?.labels)} />,
  Line: (props) => <div data-testid="mock-line-chart" data-labels={JSON.stringify(props.data?.labels)} />,
}));

describe('AutoML Benchmark Feature Response & Graph Selection', () => {
  const sampleDataset = {
    id: 1,
    name: 'Clinical Trial Cohort',
    rowCount: 500,
    columnCount: 4,
    columns: [
      { id: 1, columnName: 'patient_id', dataType: 'IDENTIFIER', isTarget: false, isExcluded: false },
      { id: 2, columnName: 'age', dataType: 'NUMERIC', isTarget: false, isExcluded: false, uniqueCount: 50 },
      { id: 3, columnName: 'cholesterol', dataType: 'NUMERIC', isTarget: false, isExcluded: false, uniqueCount: 80 },
      { id: 4, columnName: 'outcome', dataType: 'CATEGORICAL', isTarget: true, isExcluded: false, uniqueCount: 2 },
    ],
  };

  const sampleAutoMlResults = {
    targetColumn: 'outcome',
    problemType: 'CLASSIFICATION',
    scientificSummary: 'Random Forest achieved highest holdout F1 score (0.88).',
    models: [
      {
        id: 101,
        modelName: 'Random Forest',
        algorithm: 'RANDOM_FOREST',
        isBestModel: true,
        valMetrics: { f1: 0.88, accuracy: 0.89 },
        testMetrics: { f1: 0.87, accuracy: 0.88 },
      },
    ],
    bestModel: {
      modelName: 'Random Forest',
      algorithm: 'RANDOM_FOREST',
      valMetrics: { f1: 0.88 },
    },
    featureImportances: [
      { rank: 1, featureName: 'cholesterol', relativePercentage: 62.5, direction: 'POSITIVE', interpretation: 'Higher cholesterol strongly increases outcome probability.' },
      { rank: 2, featureName: 'age', relativePercentage: 37.5, direction: 'POSITIVE', interpretation: 'Older age moderately increases outcome probability.' },
    ],
    fairness: null,
  };

  const sampleNumericEffect = {
    featureName: 'cholesterol',
    kind: 'NUMERIC',
    problemType: 'CLASSIFICATION',
    targetColumn: 'outcome',
    referenceValue: 180.0,
    referencePrediction: 0.42,
    referenceClass: 'No',
    supported: true,
    points: [
      { x: 120.0, prediction: 0.15, probability: 0.15 },
      { x: 180.0, prediction: 0.42, probability: 0.42 },
      { x: 240.0, prediction: 0.85, probability: 0.85 },
    ],
    scientificNote: 'Marginal conditional response curve for cholesterol.',
  };

  const sampleAgeEffect = {
    featureName: 'age',
    kind: 'NUMERIC',
    problemType: 'CLASSIFICATION',
    targetColumn: 'outcome',
    referenceValue: 50.0,
    referencePrediction: 0.42,
    referenceClass: 'No',
    supported: true,
    points: [
      { x: 20.0, prediction: 0.20, probability: 0.20 },
      { x: 50.0, prediction: 0.42, probability: 0.42 },
      { x: 80.0, prediction: 0.70, probability: 0.70 },
    ],
    scientificNote: 'Marginal conditional response curve for age.',
  };

  beforeEach(() => {
    vi.clearAllMocks();
    if (typeof localStorage !== 'undefined' && localStorage.clear) {
      localStorage.clear();
    }

    datasetService.listDatasets.mockResolvedValue({ data: [sampleDataset] });
    datasetService.getDatasets.mockResolvedValue({ data: [sampleDataset] });
    datasetService.getDataset.mockResolvedValue({ data: sampleDataset });
    datasetService.getWorkflowStatus.mockResolvedValue({ data: { stages: {} } });

    analysisService.getLatestAutoMl.mockResolvedValue({ data: sampleAutoMlResults });
    analysisService.getWhatIfSchema.mockResolvedValue({
      data: {
        features: [
          { name: 'cholesterol', kind: 'NUMERIC', referenceValue: 180.0 },
          { name: 'age', kind: 'NUMERIC', referenceValue: 50.0 },
        ],
      },
    });
    analysisService.getFeatureEffect.mockImplementation((dsId, feat) => {
      if (feat === 'age') {
        return Promise.resolve({ data: sampleAgeEffect });
      }
      return Promise.resolve({ data: sampleNumericEffect });
    });
  });

  const renderComponent = (initialPath = '/datasets/1/ml') => {
    return render(
      <MemoryRouter initialEntries={[initialPath]}>
        <WorkflowProvider>
          <Routes>
            <Route path="/datasets/:id/ml" element={<AutoMlPage />} />
            <Route path="/datasets/:id/xai" element={<AutoMlPage />} />
          </Routes>
        </WorkflowProvider>
      </MemoryRouter>
    );
  };

  it('renders the ML Benchmark feature response section and initial feature graph', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('benchmark-feature-section')).toBeInTheDocument();
    });

    expect(screen.getByText(/ML Benchmark: Feature Response & Effect Curve/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Select feature to inspect model response/i)).toBeInTheDocument();

    // Chart container is rendered
    expect(screen.getByTestId('benchmark-feature-chart-container')).toBeInTheDocument();
    expect(screen.getByTestId('mock-line-chart')).toBeInTheDocument();
    expect(screen.getByText(/Marginal conditional response curve for cholesterol/i)).toBeInTheDocument();
  });

  it('updates the benchmark graph when selecting a different feature from the dropdown', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('benchmark-feature-section')).toBeInTheDocument();
    });

    const select = screen.getByLabelText(/Select feature to inspect model response/i);
    fireEvent.change(select, { target: { value: 'age' } });

    await waitFor(() => {
      expect(analysisService.getFeatureEffect).toHaveBeenCalledWith('1', 'age');
      expect(screen.getByText(/Marginal conditional response curve for age/i)).toBeInTheDocument();
    });
  });

  it('updates the benchmark graph when clicking a feature in the importance table', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('benchmark-feature-section')).toBeInTheDocument();
    });

    // Find row with 'age' in feature importances table and click it
    const ageCell = screen.getByRole('cell', { name: /^age/ });
    fireEvent.click(ageCell);

    await waitFor(() => {
      expect(analysisService.getFeatureEffect).toHaveBeenCalledWith('1', 'age');
    });
  });

  it('displays informative notice when feature effect is unsupported or fails', async () => {
    analysisService.getFeatureEffect.mockImplementation(() =>
      Promise.resolve({
        data: {
          featureName: 'cholesterol',
          supported: false,
          unsupportedReason: "Feature 'cholesterol' has zero variance and cannot produce a response curve.",
        },
      })
    );

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('benchmark-feature-section')).toBeInTheDocument();
      expect(screen.getByTestId('benchmark-feature-empty')).toBeInTheDocument();
      expect(
        screen.getByText(/Feature 'cholesterol' has zero variance and cannot produce a response curve/i)
      ).toBeInTheDocument();
    });
  });
});
