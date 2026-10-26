import { describe, expect, it } from 'vitest';
import { validateProductionApiBaseUrl } from '../../config/apiBaseUrl.js';

describe('production API base URL validation', () => {
  it('accepts an absolute HTTPS backend URL', () => {
    expect(validateProductionApiBaseUrl('https://autoscientist-ai-backend.onrender.com/api')).toBe(
      'https://autoscientist-ai-backend.onrender.com/api'
    );
  });

  it('accepts a custom HTTPS domain', () => {
    expect(validateProductionApiBaseUrl('https://api.autoscientist.example.com/api')).toBe(
      'https://api.autoscientist.example.com/api'
    );
  });

  it('rejects a missing value', () => {
    expect(() => validateProductionApiBaseUrl('')).toThrow(/VITE_API_BASE_URL is required/);
    expect(() => validateProductionApiBaseUrl(undefined)).toThrow(/VITE_API_BASE_URL is required/);
  });

  it('rejects a relative URL', () => {
    expect(() => validateProductionApiBaseUrl('/api')).toThrow(/absolute URL/);
  });

  it('rejects plain HTTP', () => {
    expect(() => validateProductionApiBaseUrl('http://backend.example.com/api')).toThrow(/https/);
  });

  it.each([
    'http://localhost:8080/api',
    'https://localhost/api',
    'https://127.0.0.1/api',
    'https://127.10.20.30/api',
    'https://0.0.0.0/api',
    'https://[::1]/api',
    'https://host.docker.internal/api',
    'https://api.localhost/api',
  ])('rejects loopback URL %s', (url) => {
    expect(() => validateProductionApiBaseUrl(url)).toThrow(/localhost\/loopback/);
  });
});
