import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from '../services/api';
import { analysisService } from '../services/analysisService';

vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('analysisService API Client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches associations and triggers refresh', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: [{ id: 1, pValue: 0.01 }] } });
    api.post.mockResolvedValue({ data: { success: true, data: [] } });

    const assocs = await analysisService.getAssociations(42);
    expect(api.get).toHaveBeenCalledWith('/datasets/42/associations');
    expect(assocs.success).toBe(true);

    const refreshed = await analysisService.refreshAssociations(42);
    expect(api.post).toHaveBeenCalledWith('/datasets/42/associations/refresh');
    expect(refreshed.success).toBe(true);
  });

  it('fetches correlation matrix and boxplots', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: {} } });

    await analysisService.getCorrelationMatrix(10);
    expect(api.get).toHaveBeenCalledWith('/datasets/10/eda/correlations');

    await analysisService.getBoxPlots(10);
    expect(api.get).toHaveBeenCalledWith('/datasets/10/eda/boxplots');
  });

  it('queries scatter plot, grouped distributions and time series with correct query params', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: {} } });

    await analysisService.getScatter(5, 'age', 'bmi');
    expect(api.get).toHaveBeenCalledWith('/datasets/5/eda/scatter', {
      params: { xVar: 'age', yVar: 'bmi' },
    });

    await analysisService.getGroupedSummary(5, 'income', 'cohort');
    expect(api.get).toHaveBeenCalledWith('/datasets/5/eda/grouped', {
      params: { numericVar: 'income', categoricalVar: 'cohort' },
    });

    await analysisService.getGroupDistributions(5, 'income', 'cohort');
    expect(api.get).toHaveBeenCalledWith('/datasets/5/eda/group-distributions', {
      params: { numericVar: 'income', categoricalVar: 'cohort' },
    });

    await analysisService.getTimeSeries(5, 'date_col', 'price', 1);
    expect(api.get).toHaveBeenCalledWith('/datasets/5/eda/timeseries', {
      params: { timeVar: 'date_col', valueVar: 'price', aggregation: 1 },
    });
  });

  it('fetches missingness, histograms, categorical summaries and missingness matrix', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: [] } });

    await analysisService.getHistograms(3);
    expect(api.get).toHaveBeenCalledWith('/datasets/3/eda/histograms');

    await analysisService.getMissingness(3);
    expect(api.get).toHaveBeenCalledWith('/datasets/3/eda/missingness');

    await analysisService.getCategoricalSummaries(3);
    expect(api.get).toHaveBeenCalledWith('/datasets/3/eda/categorical');

    await analysisService.getMissingnessMatrix(3, 100);
    expect(api.get).toHaveBeenCalledWith('/datasets/3/eda/missingness-matrix', {
      params: { limit: 100 },
    });
  });

  it('fetches evidence graph and reliability scorecard', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: { nodes: [] } } });

    await analysisService.getEvidenceGraph(8);
    expect(api.get).toHaveBeenCalledWith('/datasets/8/evidence');

    await analysisService.getReliabilityScorecard(8);
    expect(api.get).toHaveBeenCalledWith('/datasets/8/reliability');
  });

  it('handles AutoML training, latest model, what-if schema and simulation', async () => {
    api.post.mockResolvedValue({ data: { success: true, data: { runId: 101 } } });
    api.get.mockResolvedValue({ data: { success: true, data: { features: [] } } });

    const trainPayload = { targetColumn: 'label', algorithms: ['RANDOM_FOREST'] };
    await analysisService.trainAutoMl(15, trainPayload);
    expect(api.post).toHaveBeenCalledWith('/datasets/15/automl/train', trainPayload);

    await analysisService.getLatestAutoMl(15);
    expect(api.get).toHaveBeenCalledWith('/datasets/15/automl/latest');

    await analysisService.getWhatIfSchema(15);
    expect(api.get).toHaveBeenCalledWith('/datasets/15/automl/whatif/schema');

    const whatIfPayload = { featureValues: { age: 30 } };
    await analysisService.simulateWhatIf(15, whatIfPayload);
    expect(api.post).toHaveBeenCalledWith('/datasets/15/automl/whatif', whatIfPayload);
  });

  it('calls drift comparison, askDataset and dashboard stats', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: {} } });
    api.post.mockResolvedValue({ data: { success: true, data: { answer: 'Found 10 rows' } } });

    await analysisService.compareDrift(1, 2);
    expect(api.get).toHaveBeenCalledWith('/drift/compare', {
      params: { referenceId: 1, targetId: 2 },
    });

    await analysisService.askDataset(1, 'What is the average age?');
    expect(api.post).toHaveBeenCalledWith('/datasets/1/ask', { query: 'What is the average age?' });

    await analysisService.getDashboardStats();
    expect(api.get).toHaveBeenCalledWith('/dashboard/stats');

    await analysisService.getFeatureEffect(1, 'cholesterol');
    expect(api.get).toHaveBeenCalledWith('/datasets/1/automl/feature-effect', {
      params: { feature: 'cholesterol' },
    });
  });

  it('supports report generation, report retrieval and multi-format blob downloads', async () => {
    api.post.mockResolvedValue({ data: { success: true, data: { id: 99 } } });
    api.get.mockResolvedValue({ data: new Blob(['content']) });

    await analysisService.generateReport(7, { title: 'New Report' });
    expect(api.post).toHaveBeenCalledWith('/datasets/7/reports', { title: 'New Report' });

    await analysisService.listReports();
    expect(api.get).toHaveBeenCalledWith('/reports');

    await analysisService.getDatasetReports(7);
    expect(api.get).toHaveBeenCalledWith('/datasets/7/reports');

    await analysisService.getReport(99);
    expect(api.get).toHaveBeenCalledWith('/reports/99');

    await analysisService.downloadReportPdf(99);
    expect(api.get).toHaveBeenCalledWith('/reports/99/pdf', { responseType: 'blob' });

    await analysisService.downloadReportCsv(99);
    expect(api.get).toHaveBeenCalledWith('/reports/99/export/csv', { responseType: 'blob' });

    await analysisService.downloadReportExcel(99);
    expect(api.get).toHaveBeenCalledWith('/reports/99/export/excel', { responseType: 'blob' });

    await analysisService.downloadReportMarkdown(99);
    expect(api.get).toHaveBeenCalledWith('/reports/99/export/markdown', { responseType: 'blob' });
  });
});
