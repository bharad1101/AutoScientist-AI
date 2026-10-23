import api from './api';

export const researchService = {
  async createResearchQuestion(datasetId, questionText, predictorVariable, targetVariable) {
    const res = await api.post(`/datasets/${datasetId}/research-questions`, {
      questionText,
      predictorVariable,
      targetVariable,
    });
    return res.data;
  },

  async getDatasetResearchQuestions(datasetId) {
    const res = await api.get(`/datasets/${datasetId}/research-questions`);
    return res.data;
  },

  async getResearchQuestion(id) {
    const res = await api.get(`/research-questions/${id}`);
    return res.data;
  },

  async executeHypothesisTest(rqId, hypothesisId) {
    const res = await api.post(`/research-questions/${rqId}/hypotheses/${hypothesisId}/execute`);
    return res.data;
  },

  async getDatasetFindings(datasetId) {
    const res = await api.get(`/datasets/${datasetId}/findings`);
    return res.data;
  },

  async getFinding(id) {
    const res = await api.get(`/findings/${id}`);
    return res.data;
  },

  async listExperiments() {
    const res = await api.get('/experiments');
    return res.data;
  },

  async getExperiment(id) {
    const res = await api.get(`/experiments/${id}`);
    return res.data;
  },

  async getExperimentComparison(experimentId) {
    const res = await api.get(`/experiments/${experimentId}/comparison`);
    return res.data;
  },
};
