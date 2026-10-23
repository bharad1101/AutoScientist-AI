import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor to inject JWT Bearer token into outgoing requests
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('autoscientist_token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Interceptor to handle 401 Unauthorized globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('autoscientist_token');
      localStorage.removeItem('autoscientist_user');

      // Public routes must NEVER be redirected to /login on 401
      const pathname = typeof window !== 'undefined' ? window.location.pathname : '';
      const isPublicPath =
        pathname === '/' ||
        pathname === '/home' ||
        pathname === '/login' ||
        pathname === '/register';

      // Auth-check endpoints and requests with skipAuthRedirect must not trigger global navigation
      const isAuthCheck = error.config?.url?.includes('/auth/me');
      const skipRedirect = Boolean(error.config?.skipAuthRedirect);

      if (!isPublicPath && !isAuthCheck && !skipRedirect) {
        const returnUrl = encodeURIComponent(pathname + (window.location.search || ''));
        window.location.href = `/login?redirect=${returnUrl}`;
      }
    }
    return Promise.reject(error);
  }
);

export default api;
