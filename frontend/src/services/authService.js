import api from './api';
import { retryOnTransient } from './apiErrors';

export const authService = {
  async register(name, email, password) {
    const res = await api.post('/auth/register', { name, email, password });
    return res.data;
  },

  async login(email, password) {
    const res = await api.post('/auth/login', { email, password });
    return res.data;
  },

  async forgotPassword(email) {
    const res = await retryOnTransient(
      () => api.post('/auth/forgot-password', { email }, { timeout: 10000 }),
      {
        attempts: 2,
        baseDelayMs: 500,
        maxDelayMs: 1000,
        shouldRetry: (error) => [502, 503, 504].includes(error?.response?.status),
      }
    );
    return res.data;
  },

  async resetPassword(token, newPassword) {
    const res = await api.post('/auth/reset-password', { token, newPassword });
    return res.data;
  },

  async getCurrentUser() {
    const res = await api.get('/auth/me');
    return res.data;
  },

  async logout() {
    try {
      await api.post('/auth/logout');
    } finally {
      localStorage.removeItem('autoscientist_token');
      localStorage.removeItem('autoscientist_user');
    }
  },
};
