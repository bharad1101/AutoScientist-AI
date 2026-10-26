/**
 * api.test.js — Integration-style tests for the actual Axios API client
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { validateProductionApiBaseUrl } from '../../config/apiBaseUrl.js';

describe('API client (actual service) — base URL resolution', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('exports the axios instance created by api.js', async () => {
    const api = (await import('../services/api.js')).default;
    expect(api).toBeDefined();
    expect(typeof api.interceptors?.request?.use).toBe('function');
    expect(typeof api.interceptors?.response?.use).toBe('function');
  });

  it('uses fallback /api when VITE_API_BASE_URL not set', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '');
    vi.resetModules();
    const api = (await import('../services/api.js')).default;
    expect(api.defaults?.baseURL).toBe('/api');
    vi.unstubAllEnvs();
  });

  it('uses VITE_API_BASE_URL when explicitly configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://autoscientist-ai-backend.onrender.com/api');
    vi.resetModules();
    const api = (await import('../services/api.js')).default;
    expect(api.defaults?.baseURL).toBe('https://autoscientist-ai-backend.onrender.com/api');
    vi.unstubAllEnvs();
  });

  it('rejects localhost in a production API base URL', () => {
    expect(() => validateProductionApiBaseUrl('http://localhost:8080/api')).toThrow(/localhost/);
    expect(() => validateProductionApiBaseUrl('http://127.0.0.1:8080/api')).toThrow(/localhost/);
  });
});

describe('API client (actual service) — request interceptor', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('attaches Authorization: Bearer <token> when autoscientist_token exists in localStorage', async () => {
    localStorage.setItem('autoscientist_token', 'test.jwt.token');
    const api = (await import('../services/api.js')).default;
    const config = { headers: {} };
    const result = await api.interceptors.request.handlers[0].fulfilled(config);
    expect(result.headers['Authorization']).toBe('Bearer test.jwt.token');
  });

  it('does NOT attach Authorization header when no token in localStorage', async () => {
    localStorage.clear();
    const api = (await import('../services/api.js')).default;
    const config = { headers: {} };
    const result = await api.interceptors.request.handlers[0].fulfilled(config);
    expect(result.headers['Authorization']).toBeUndefined();
  });

  it('Authorization header format is exactly "Bearer <token>"', async () => {
    localStorage.setItem('autoscientist_token', 'eyJhbGciOiJIUzI1NiJ9.test.signature');
    const api = (await import('../services/api.js')).default;
    const config = { headers: {} };
    const result = await api.interceptors.request.handlers[0].fulfilled(config);
    expect(result.headers['Authorization']).toMatch(/^Bearer /);
    expect(result.headers['Authorization']).toBe('Bearer eyJhbGciOiJIUzI1NiJ9.test.signature');
  });
});

describe('API client (actual service) — response interceptor (401 handling)', () => {
  const originalWindow = global.window;

  beforeEach(() => {
    localStorage.clear();
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    global.window = originalWindow;
  });

  it('clears localStorage on 401 and does not redirect on public paths', async () => {
    localStorage.setItem('autoscientist_token', 'token123');
    localStorage.setItem('autoscientist_user', '{"id":1}');
    const hrefSetter = vi.fn();
    vi.stubGlobal('window', {
      location: {
        pathname: '/login',
        search: '',
        set href(val) { hrefSetter(val); },
      },
    });

    const error = {
      response: { status: 401 },
      config: { url: '/api/something' },
    };

    const api = (await import('../services/api.js')).default;
    await expect(api.interceptors.response.handlers[0].rejected(error)).rejects.toBe(error);
    expect(localStorage.getItem('autoscientist_token')).toBeNull();
    expect(localStorage.getItem('autoscientist_user')).toBeNull();
    expect(hrefSetter).not.toHaveBeenCalled();
  });

  it('does NOT redirect when 401 comes from /auth/me (auth-check exempt)', async () => {
    localStorage.setItem('autoscientist_token', 'token123');
    localStorage.setItem('autoscientist_user', '{"id":1}');
    const hrefSetter = vi.fn();
    vi.stubGlobal('window', {
      location: {
        pathname: '/dashboard',
        search: '',
        set href(val) { hrefSetter(val); },
      },
    });

    const error = {
      response: { status: 401 },
      config: { url: '/auth/me' },
    };

    const api = (await import('../services/api.js')).default;
    await expect(api.interceptors.response.handlers[0].rejected(error)).rejects.toBe(error);
    expect(localStorage.getItem('autoscientist_token')).toBeNull();
    expect(localStorage.getItem('autoscientist_user')).toBeNull();
    expect(hrefSetter).not.toHaveBeenCalled();
  });

  it('does NOT redirect when on public paths /, /home, /register', async () => {
    const paths = ['/', '/home', '/register'];
    for (const pathname of paths) {
      localStorage.clear();
      vi.resetModules();
      const hrefSetter = vi.fn();
      vi.stubGlobal('window', {
        location: {
          pathname,
          search: '',
          set href(val) { hrefSetter(val); },
        },
      });
      const error = {
        response: { status: 401 },
        config: { url: '/api/something' },
      };
      const api = (await import('../services/api.js')).default;
      await expect(api.interceptors.response.handlers[0].rejected(error)).rejects.toBe(error);
      expect(hrefSetter).not.toHaveBeenCalled();
      expect(localStorage.getItem('autoscientist_token')).toBeNull();
    }
  });

  it('does NOT redirect when skipAuthRedirect flag is set on request config', async () => {
    localStorage.setItem('autoscientist_token', 'token123');
    localStorage.setItem('autoscientist_user', '{"id":1}');
    const hrefSetter = vi.fn();
    vi.stubGlobal('window', {
      location: {
        pathname: '/dashboard',
        search: '',
        set href(val) { hrefSetter(val); },
      },
    });

    const error = {
      response: { status: 401 },
      config: { url: '/api/datasets', skipAuthRedirect: true },
    };

    const api = (await import('../services/api.js')).default;
    await expect(api.interceptors.response.handlers[0].rejected(error)).rejects.toBe(error);
    expect(localStorage.getItem('autoscientist_token')).toBeNull();
    expect(localStorage.getItem('autoscientist_user')).toBeNull();
    expect(hrefSetter).not.toHaveBeenCalled();
  });

  it('redirects to /login with returnUrl for protected routes on 401 (when not exempt)', async () => {
    localStorage.setItem('autoscientist_token', 'token123');
    localStorage.setItem('autoscientist_user', '{"id":1}');
    const hrefSetter = vi.fn();
    vi.stubGlobal('window', {
      location: {
        pathname: '/datasets',
        search: '?sort=name',
        set href(val) { hrefSetter(val); },
      },
    });

    const error = {
      response: { status: 401 },
      config: { url: '/api/datasets' },
    };

    const api = (await import('../services/api.js')).default;
    await expect(api.interceptors.response.handlers[0].rejected(error)).rejects.toBe(error);
    expect(localStorage.getItem('autoscientist_token')).toBeNull();
    expect(localStorage.getItem('autoscientist_user')).toBeNull();
    expect(hrefSetter).toHaveBeenCalled();
    const redirectUrl = hrefSetter.mock.calls[0][0];
    expect(redirectUrl).toMatch(/^\/login\?redirect=/);
  });

  it('non-401 errors are passed through without touching localStorage or redirecting', async () => {
    localStorage.setItem('autoscientist_token', 'token123');
    localStorage.setItem('autoscientist_user', '{"id":1}');
    const hrefSetter = vi.fn();
    vi.stubGlobal('window', {
      location: {
        pathname: '/dashboard',
        search: '',
        set href(val) { hrefSetter(val); },
      },
    });

    const error = { response: { status: 500 }, config: { url: '/api/datasets' } };

    const api = (await import('../services/api.js')).default;
    await expect(api.interceptors.response.handlers[0].rejected(error)).rejects.toBe(error);
    expect(localStorage.getItem('autoscientist_token')).toBe('token123');
    expect(localStorage.getItem('autoscientist_user')).toBe('{"id":1}');
    expect(hrefSetter).not.toHaveBeenCalled();
  });

  it('network errors without response object are passed through safely', async () => {
    localStorage.setItem('autoscientist_token', 'token123');
    localStorage.setItem('autoscientist_user', '{"id":1}');
    const hrefSetter = vi.fn();
    vi.stubGlobal('window', {
      location: {
        pathname: '/dashboard',
        search: '',
        set href(val) { hrefSetter(val); },
      },
    });

    const error = { request: {}, config: { url: '/api/datasets' } };

    const api = (await import('../services/api.js')).default;
    await expect(api.interceptors.response.handlers[0].rejected(error)).rejects.toBe(error);
    expect(localStorage.getItem('autoscientist_token')).toBe('token123');
    expect(localStorage.getItem('autoscientist_user')).toBe('{"id":1}');
    expect(hrefSetter).not.toHaveBeenCalled();
  });
});
