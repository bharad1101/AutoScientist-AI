import api from './api';

export const datasetService = {
  async uploadDataset(file, name, description, options) {
    const formData = new FormData();
    formData.append('file', file);
    if (name) formData.append('name', name);
    if (description) formData.append('description', description);
    if (options) formData.append('options', JSON.stringify(options));

    const res = await api.post('/datasets/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  /**
   * Stateless import-wizard preview: sheets, header row, inferred types, data
   * preview and warnings. Nothing is persisted by this call.
   */
  async previewImport(file, options) {
    const formData = new FormData();
    formData.append('file', file);
    if (options) formData.append('options', JSON.stringify(options));

    const res = await api.post('/datasets/preview', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  async reprofileDataset(id, overrides) {
    const res = await api.post(`/datasets/${id}/reprofile`, overrides || {});
    return res.data;
  },

  async updateExclusions(id, excludedColumns) {
    const res = await api.put(`/datasets/${id}/exclusions`, { excludedColumns });
    return res.data;
  },

  async listDatasets() {
    const res = await api.get('/datasets');
    return res.data;
  },

  async getDatasets() {
    return this.listDatasets();
  },

  async getDataset(id) {
    const res = await api.get(`/datasets/${id}`);
    return res.data;
  },

  async deleteDataset(id) {
    const res = await api.delete(`/datasets/${id}`);
    return res.data;
  },

  async getWorkflowStatus(id) {
    const res = await api.get(`/datasets/${id}/workflow-status`);
    return res.data;
  },
};
