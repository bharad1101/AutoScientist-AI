const LOOPBACK_HOSTNAMES = new Set([
  'localhost',
  '127.0.0.1',
  '0.0.0.0',
  '::1',
  '[::1]',
  'host.docker.internal',
]);

const LOOPBACK_IPV4 = /^127(?:\.\d{1,3}){3}$/;

export function validateProductionApiBaseUrl(value) {
  const raw = typeof value === 'string' ? value.trim() : '';

  if (!raw) {
    throw new Error(
      'Production build refused: VITE_API_BASE_URL is required. ' +
        'Set it to the deployed backend URL ending in /api (for example https://your-backend.onrender.com/api).'
    );
  }

  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error(
      `Production build refused: VITE_API_BASE_URL must be an absolute URL, received "${raw}".`
    );
  }

  const hostname = url.hostname.toLowerCase();
  if (LOOPBACK_HOSTNAMES.has(hostname) || LOOPBACK_IPV4.test(hostname) || hostname.endsWith('.localhost')) {
    throw new Error(
      `Production build refused: VITE_API_BASE_URL points to localhost/loopback ("${raw}"). ` +
        'A localhost URL would make deployed users call their own machine instead of the production backend.'
    );
  }

  if (url.protocol !== 'https:') {
    throw new Error(
      `Production build refused: VITE_API_BASE_URL must use https, received "${raw}".`
    );
  }

  return raw;
}
