import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { validateProductionApiBaseUrl } from './config/apiBaseUrl.js';

export default defineConfig(({ command, mode }) => {
  const fileEnv = loadEnv(mode, process.cwd(), '');
  const apiBaseUrl = process.env.VITE_API_BASE_URL ?? fileEnv.VITE_API_BASE_URL;
  const proxyMode = (process.env.VITE_API_PROXY_MODE ?? fileEnv.VITE_API_PROXY_MODE ?? '')
    .trim()
    .toLowerCase();

  if (command === 'build') {
    if (proxyMode && proxyMode !== 'same-origin') {
      throw new Error(
        `Production build refused: VITE_API_PROXY_MODE must be "same-origin" or unset, received "${proxyMode}".`
      );
    }
    const normalizedApiBaseUrl = validateProductionApiBaseUrl(apiBaseUrl, {
      allowSameOrigin: proxyMode === 'same-origin',
    });
    process.env.VITE_API_BASE_URL = normalizedApiBaseUrl;
  }

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          secure: false,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.js',
    },
    build: {
      // manual chunks intentionally left to Rolldown's shared-chunk analysis
      chunkSizeWarningLimit: 600,
    },
  };
});
