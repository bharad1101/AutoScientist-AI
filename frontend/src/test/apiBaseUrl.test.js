import { describe, expect, it } from 'vitest';
import { validateProductionApiBaseUrl } from '../../config/apiBaseUrl.js';

describe('production API base URL validation', () => {
  it('accepts a test-only absolute HTTPS backend URL', () => {
    expect(validateProductionApiBaseUrl('https://api.example.com/api')).toBe(
      'https://api.example.com/api'
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

  it('rejects a relative URL by default', () => {
    expect(() => validateProductionApiBaseUrl('/api')).toThrow(/reverse proxy/);
    expect(() => validateProductionApiBaseUrl('/other')).toThrow(/reverse proxy/);
  });

  it('accepts same-origin /api only with an explicit reverse-proxy opt-in', () => {
    expect(validateProductionApiBaseUrl('/api', { allowSameOrigin: true })).toBe('/api');
    expect(() => validateProductionApiBaseUrl('/other', { allowSameOrigin: true })).toThrow(
      /reverse proxy/
    );
    expect(() => validateProductionApiBaseUrl('/api/', { allowSameOrigin: true })).toThrow(
      /reverse proxy/
    );
  });

  it('rejects plain HTTP', () => {
    expect(() => validateProductionApiBaseUrl('http://backend.example.com/api')).toThrow(/https/);
  });

  it('rejects unsupported protocols', () => {
    expect(() => validateProductionApiBaseUrl('ftp://api.example.com/api')).toThrow(/https/);
    expect(() => validateProductionApiBaseUrl('javascript:alert(1)')).toThrow(/https/);
  });

  it('rejects malformed URLs', () => {
    expect(() => validateProductionApiBaseUrl('not-a-url')).toThrow(/absolute URL/);
    expect(() => validateProductionApiBaseUrl('https://')).toThrow(/absolute URL/);
  });

  it('rejects a URL that is missing the /api prefix', () => {
    expect(() => validateProductionApiBaseUrl('https://api.example.com')).toThrow(
      /must end with \/api/
    );
    expect(() => validateProductionApiBaseUrl('https://api.example.com/auth')).toThrow(
      /must end with \/api/
    );
  });

  it('rejects a trailing slash after /api', () => {
    expect(() => validateProductionApiBaseUrl('https://api.example.com/api/')).toThrow(
      /must end with \/api/
    );
  });

  it('normalizes the default HTTPS port', () => {
    expect(validateProductionApiBaseUrl('https://api.example.com:443/api')).toBe(
      'https://api.example.com/api'
    );
  });

  it('rejects credentials', () => {
    expect(() =>
      validateProductionApiBaseUrl('https://user:password@api.example.com/api')
    ).toThrow(/must not include credentials/);
  });

  it('rejects a non-standard port', () => {
    expect(() => validateProductionApiBaseUrl('https://api.example.com:8443/api')).toThrow(
      /must not specify a non-standard port/
    );
  });

  it('rejects a query string or fragment', () => {
    expect(() =>
      validateProductionApiBaseUrl('https://api.example.com/api?tenant=1')
    ).toThrow(/must not include a query string or fragment/);
    expect(() => validateProductionApiBaseUrl('https://api.example.com/api#section')).toThrow(
      /must not include a query string or fragment/
    );
  });

  it.each([
    'http://localhost:8080/api',
    'https://localhost/api',
    'https://localhost./api',
    'https://127.0.0.1/api',
    'https://127.10.20.30/api',
    'https://0.0.0.0/api',
    'https://[::1]/api',
    'https://[::ffff:127.0.0.1]/api',
    'https://[::ffff:0.0.0.0]/api',
    'https://host.docker.internal/api',
    'https://api.localhost/api',
    'https://foo.localhost./api',
    'https://localtest.me/api',
    'https://lvh.me/api',
    'https://127.0.0.1.nip.io/api',
    'https://127-0-0-1.sslip.io/api',
    'https://2130706433/api',
    'https://0x7f000001/api',
  ])('rejects loopback URL %s', (url) => {
    expect(() => validateProductionApiBaseUrl(url)).toThrow(/localhost\/loopback/);
  });
});
