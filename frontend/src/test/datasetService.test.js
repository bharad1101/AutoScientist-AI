import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from '../services/api';
import { datasetService } from '../services/datasetService';

vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('datasetService API Client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uploads dataset with multipart form data', async () => {
    api.post.mockResolvedValue({ data: { success: true, data: { id: 1 } } });
    const dummyFile = new File(['col1,col2\n1,2'], 'test.csv', { type: 'text/csv' });

    const result = await datasetService.uploadDataset(dummyFile, 'My Dataset', 'Test Desc', { delimiter: ',' });
    expect(api.post).toHaveBeenCalledWith(
      '/datasets/upload',
      expect.any(FormData),
      expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    );
    expect(result.data.id).toBe(1);
  });

  it('previews import without persisting', async () => {
    api.post.mockResolvedValue({ data: { success: true, data: { inferredTypes: { col1: 'NUMERIC' } } } });
    const dummyFile = new File(['a,b'], 'test.csv', { type: 'text/csv' });

    const preview = await datasetService.previewImport(dummyFile, { headerRow: 1 });
    expect(api.post).toHaveBeenCalledWith(
      '/datasets/preview',
      expect.any(FormData),
      expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    );
    expect(preview.data.inferredTypes.col1).toBe('NUMERIC');
  });

  it('reprofiles dataset and updates exclusions', async () => {
    api.post.mockResolvedValue({ data: { success: true, data: { qualityScore: 92 } } });
    api.put.mockResolvedValue({ data: { success: true } });

    await datasetService.reprofileDataset(4, { typeOverrides: { colA: 'CATEGORICAL' } });
    expect(api.post).toHaveBeenCalledWith('/datasets/4/reprofile', { typeOverrides: { colA: 'CATEGORICAL' } });

    await datasetService.updateExclusions(4, ['colB', 'colC']);
    expect(api.put).toHaveBeenCalledWith('/datasets/4/exclusions', { excludedColumns: ['colB', 'colC'] });
  });

  it('lists, gets, deletes datasets and gets workflow status', async () => {
    api.get.mockResolvedValue({ data: { success: true, data: [] } });
    api.delete.mockResolvedValue({ data: { success: true } });

    await datasetService.listDatasets();
    expect(api.get).toHaveBeenCalledWith('/datasets');

    await datasetService.getDatasets();
    expect(api.get).toHaveBeenCalledWith('/datasets');

    await datasetService.getDataset(12);
    expect(api.get).toHaveBeenCalledWith('/datasets/12');

    await datasetService.deleteDataset(12);
    expect(api.delete).toHaveBeenCalledWith('/datasets/12');

    await datasetService.getWorkflowStatus(12);
    expect(api.get).toHaveBeenCalledWith('/datasets/12/workflow-status');
  });
});
