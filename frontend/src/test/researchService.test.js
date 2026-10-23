import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from '../services/api';
import { researchService } from '../services/researchService';

vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('researchService API Client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('creates and lists research questions and fetches by id', async () => {
    api.post.mockResolvedValue({ data: { success: true, data: { id: 10 } } });
    api.get.mockResolvedValue({ data: { success: true, data: [{ id: 10 }] } });

    await researchService.createResearchQuestion(5, 'Does feature A predict B?', 'featA', 'featB');
    expect(api.post).toHaveBeenCalledWith('/datasets/5/research-questions', {
      questionText: 'Does feature A predict B?',
      predictorVariable: 'featA',
      targetVariable: 'featB',
    });

    await researchService.getDatasetResearchQuestions(5);
    expect(api.get).toHaveBeenCalledWith('/datasets/5/research-questions');

    await researchService.getResearchQuestion(10);
    expect(api.get).toHaveBeenCalledWith('/research-questions/10');
  });

  it('executes hypothesis tests and retrieves findings', async () => {
    api.post.mockResolvedValue({ data: { success: true, data: { findingId: 99 } } });
    api.get.mockResolvedValue({ data: { success: true, data: [] } });

    await researchService.executeHypothesisTest(10, 20);
    expect(api.post).toHaveBeenCalledWith('/research-questions/10/hypotheses/20/execute');

    await researchService.getDatasetFindings(5);
    expect(api.get).toHaveBeenCalledWith('/datasets/5/findings');

    await researchService.getFinding(99);
    expect(api.get).toHaveBeenCalledWith('/findings/99');
  });

  it('lists experiments and fetches experiment comparisons', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: [] } });

    await researchService.listExperiments();
    expect(api.get).toHaveBeenCalledWith('/experiments');

    await researchService.getExperiment(42);
    expect(api.get).toHaveBeenCalledWith('/experiments/42');

    await researchService.getExperimentComparison(42);
    expect(api.get).toHaveBeenCalledWith('/experiments/42/comparison');
  });
});
