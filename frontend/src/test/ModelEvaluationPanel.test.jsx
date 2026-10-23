import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import ModelEvaluationPanel from '../components/ModelEvaluationPanel';

vi.mock('react-chartjs-2', () => ({
  Line: (props) => (
    <div
      data-testid="mock-line-chart"
      data-title={props.options?.plugins?.title?.text}
      data-datasets-count={props.data?.datasets?.length}
    />
  ),
  Scatter: (props) => (
    <div
      data-testid="mock-scatter-chart"
      data-datasets-count={props.data?.datasets?.length}
      data-points-count={props.data?.datasets?.[0]?.data?.length}
    />
  ),
}));

describe('ModelEvaluationPanel', () => {
  it('renders fallback when evaluation is null or empty', () => {
    const { rerender } = render(<ModelEvaluationPanel evaluation={null} modelName="TestModel" />);
    expect(screen.getByText(/No persisted evaluation payload is available/i)).toBeInTheDocument();
    expect(screen.getByText(/TestModel/i)).toBeInTheDocument();

    rerender(<ModelEvaluationPanel evaluation={{}} />);
    expect(screen.getByText(/No persisted evaluation payload is available/i)).toBeInTheDocument();
  });

  it('renders unavailable alert when status is UNAVAILABLE', () => {
    render(
      <ModelEvaluationPanel
        evaluation={{ status: 'UNAVAILABLE', reason: 'Insufficient test instances' }}
        modelName="XGBoost"
      />
    );
    expect(screen.getByText(/Evaluation unavailable: Insufficient test instances/i)).toBeInTheDocument();
  });

  it('renders classification evaluation with confusion matrix, per-class table, ROC/PR curves, calibration, and CV', () => {
    const classificationEval = {
      status: 'AVAILABLE',
      auc: 0.942,
      averagePrecision: 0.915,
      macroF1: 0.887,
      confusionMatrix: {
        rows: [
          [45, 5],
          [8, 42],
        ],
        rowLabels: ['Class_0', 'Class_1'],
        columnLabels: ['Class_0', 'Class_1'],
        truePositives: 42,
        falsePositives: 5,
        trueNegatives: 45,
        falseNegatives: 8,
      },
      perClass: [
        { label: 'Class_0', precision: 0.849, recall: 0.9, f1: 0.874, support: 50 },
        { label: 'Class_1', precision: 0.894, recall: 0.84, f1: 0.866, support: 50 },
      ],
      rocCurve: [
        [0.0, 0.0],
        [0.1, 0.8],
        [1.0, 1.0],
      ],
      prCurve: [
        [0.8, 0.9],
        [1.0, 0.5],
      ],
      calibration: {
        brierScore: 0.082,
        bins: [
          { meanPredicted: 0.1, observedFrequency: 0.08 },
          { meanPredicted: 0.5, observedFrequency: 0.52 },
          { meanPredicted: 0.9, observedFrequency: 0.91 },
        ],
      },
      crossValidation: {
        status: 'COMPLETED',
        metric: 'accuracy',
        mean: 0.875,
        std: 0.032,
        folds: 5,
        scores: [0.85, 0.9, 0.88, 0.84, 0.9],
      },
    };

    render(
      <ModelEvaluationPanel
        evaluation={classificationEval}
        problemType="CLASSIFICATION"
        modelName="Random Forest"
      />
    );

    expect(screen.getByText(/Deep Model Evaluation — Random Forest/i)).toBeInTheDocument();

    // Confusion matrix checks
    expect(screen.getByText(/Confusion Matrix \(Holdout Test\)/i)).toBeInTheDocument();
    expect(screen.getAllByText('42').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('45').length).toBeGreaterThanOrEqual(1);

    // Per-class metrics
    expect(screen.getByText(/Macro F1: 0.887/i)).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Class_0' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Class_1' })).toBeInTheDocument();
    expect(screen.getByText('0.8490')).toBeInTheDocument();

    // ROC & PR badges
    expect(screen.getByText(/AUC: 0.942/i)).toBeInTheDocument();
    expect(screen.getByText(/Avg Precision: 0.915/i)).toBeInTheDocument();

    // Calibration
    expect(screen.getByText(/Brier score: 0.082/i)).toBeInTheDocument();

    // Cross validation
    expect(screen.getByText('0.8750')).toBeInTheDocument();
    expect(screen.getByText('Fold 1: 0.8500')).toBeInTheDocument();
  });

  it('renders ROC/PR unavailable alert when rocStatus is UNAVAILABLE_NO_SCORES', () => {
    const evalData = {
      status: 'AVAILABLE',
      rocStatus: 'UNAVAILABLE_NO_SCORES',
      confusionMatrix: {
        rows: [[10, 2], [3, 15]],
        truePositives: 15,
        falsePositives: 2,
        trueNegatives: 10,
        falseNegatives: 3,
      },
      crossValidation: {
        status: 'SKIPPED',
        reason: 'Sample size below k-fold threshold',
      },
    };

    render(
      <ModelEvaluationPanel
        evaluation={evalData}
        problemType="CLASSIFICATION"
        modelName="DecisionTree"
      />
    );

    expect(
      screen.getByText(/ROC\/PR curves unavailable: the selected algorithm does not expose continuous scores/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Sample size below k-fold threshold/i)).toBeInTheDocument();
  });

  it('renders regression diagnostics with residual stats, scatter, and cross validation', () => {
    const regressionEval = {
      status: 'AVAILABLE',
      residualSummary: {
        meanError: 0.0125,
        mae: 1.45,
        maxAbsError: 4.82,
        n: 150,
      },
      residualPoints: [
        [10.5, 0.2],
        [22.1, -0.8],
        [35.0, 1.2],
      ],
      crossValidation: {
        status: 'COMPLETED',
        metric: 'rmse',
        mean: 1.82,
        std: 0.15,
        folds: 3,
        scores: [1.7, 1.85, 1.91],
      },
    };

    render(
      <ModelEvaluationPanel
        evaluation={regressionEval}
        problemType="REGRESSION"
        modelName="Ridge Regressor"
      />
    );

    expect(screen.getByText(/Deep Model Evaluation — Ridge Regressor/i)).toBeInTheDocument();
    expect(screen.getByText(/Mean Error \(bias\)/i)).toBeInTheDocument();
    expect(screen.getByText('0.0125')).toBeInTheDocument();
    expect(screen.getByText('1.45')).toBeInTheDocument();
    expect(screen.getByText('4.82')).toBeInTheDocument();
    expect(screen.getByText('150')).toBeInTheDocument();

    expect(screen.getByRole('heading', { name: /Residual Diagnostics/i })).toBeInTheDocument();
    expect(screen.getByText('1.8200')).toBeInTheDocument();
  });
});
