import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import viteConfig from '../../vite.config.js';

const ENV_KEYS = ['VITE_API_BASE_URL', 'VITE_API_PROXY_MODE'];
const originalEnv = {};

describe('vite production configuration wiring', () => {
  beforeEach(() => {
    for (const key of ENV_KEYS) {
      originalEnv[key] = process.env[key];
      delete process.env[key];
    }
  });

  afterEach(() => {
    for (const key of ENV_KEYS) {
      if (originalEnv[key] === undefined) {
        delete process.env[key];
      } else {
        process.env[key] = originalEnv[key];
      }
    }
  });

  it('accepts a valid HTTPS build value and normalizes it', () => {
    process.env.VITE_API_BASE_URL = 'https://api.example.com:443/api';

    const config = viteConfig({ command: 'build', mode: 'production' });

    expect(process.env.VITE_API_BASE_URL).toBe('https://api.example.com/api');
    expect(config.server.port).toBe(5173);
  });

  it('rejects a localhost build value even when .env contains a dev value', () => {
    process.env.VITE_API_BASE_URL = 'http://localhost:8080/api';

    expect(() => viteConfig({ command: 'build', mode: 'production' })).toThrow(
      /localhost\/loopback/
    );
  });

  it('rejects same-origin /api without the reverse-proxy opt-in', () => {
    process.env.VITE_API_BASE_URL = '/api';

    expect(() => viteConfig({ command: 'build', mode: 'production' })).toThrow(/reverse proxy/);
  });

  it('accepts same-origin /api with the explicit reverse-proxy opt-in', () => {
    process.env.VITE_API_BASE_URL = '/api';
    process.env.VITE_API_PROXY_MODE = 'same-origin';

    viteConfig({ command: 'build', mode: 'production' });

    expect(process.env.VITE_API_BASE_URL).toBe('/api');
  });

  it('rejects an unsupported proxy mode', () => {
    process.env.VITE_API_BASE_URL = 'https://api.example.com/api';
    process.env.VITE_API_PROXY_MODE = 'vercel';

    expect(() => viteConfig({ command: 'build', mode: 'production' })).toThrow(
      /VITE_API_PROXY_MODE must be "same-origin" or unset/
    );
  });

  it('does not require a production API URL for the development server', () => {
    const config = viteConfig({ command: 'serve', mode: 'development' });

    expect(config.server.proxy['/api'].target).toBe('http://localhost:8080');
  });
});
