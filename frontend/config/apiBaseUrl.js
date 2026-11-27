const SAME_ORIGIN_API_PATH = '/api';

const LOOPBACK_HOSTNAMES = new Set([
  'localhost',
  'host.docker.internal',
  'localtest.me',
  'lvh.me',
]);

const DNS_LOOPBACK_PATTERN = /^((?:\d{1,3}(?:[-.]\d{1,3}){3}|localhost))\.(?:nip|sslip)\.io$/;

function normalizeHostname(hostname) {
  return hostname.toLowerCase().replace(/^\[/, '').replace(/\]$/, '').replace(/\.+$/, '');
}

function isLoopbackIpv4(hostname) {
  const parts = hostname.split('.').map(Number);
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false;
  }
  return parts[0] === 127 || parts[0] === 0;
}

function isLoopbackIpv6(hostname) {
  if (hostname === '::' || hostname === '::1' || /^0*(?::0*){7}$/.test(hostname)) {
    return true;
  }

  const mapped = hostname.match(/^::ffff:([0-9a-f]{1,4}):([0-9a-f]{1,4})$/i);
  if (!mapped) {
    return false;
  }

  const high = parseInt(mapped[1], 16);
  const low = parseInt(mapped[2], 16);
  const ipv4 = `${high >> 8}.${high & 0xff}.${low >> 8}.${low & 0xff}`;
  return isLoopbackIpv4(ipv4);
}

function isLoopbackHostname(hostname) {
  const normalized = normalizeHostname(hostname);

  if (LOOPBACK_HOSTNAMES.has(normalized) || normalized.endsWith('.localhost')) {
    return true;
  }

  if (normalized.includes(':')) {
    return isLoopbackIpv6(normalized);
  }

  if (isLoopbackIpv4(normalized)) {
    return true;
  }

  const dnsAlias = normalized.match(DNS_LOOPBACK_PATTERN);
  if (!dnsAlias) {
    return false;
  }
  return dnsAlias[1] === 'localhost' || isLoopbackIpv4(dnsAlias[1].replace(/-/g, '.'));
}

export function validateProductionApiBaseUrl(value, options = {}) {
  const raw = typeof value === 'string' ? value.trim() : '';

  if (!raw) {
    throw new Error(
      'Production build refused: VITE_API_BASE_URL is required. ' +
        'Set it to the deployed backend URL ending in /api, or explicitly opt into same-origin /api with VITE_API_PROXY_MODE=same-origin.'
    );
  }

  if (raw.startsWith('/')) {
    if (raw === SAME_ORIGIN_API_PATH && options.allowSameOrigin === true) {
      return SAME_ORIGIN_API_PATH;
    }
    throw new Error(
      `Production build refused: relative API path "${raw}" is only supported as /api when the deployment uses a reverse proxy ` +
        'that routes /api to the backend, and VITE_API_PROXY_MODE=same-origin is set explicitly.'
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

  if (isLoopbackHostname(url.hostname)) {
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

  if (url.username || url.password) {
    throw new Error(
      `Production build refused: VITE_API_BASE_URL must not include credentials, received "${raw}".`
    );
  }

  if (url.port && url.port !== '443') {
    throw new Error(
      `Production build refused: VITE_API_BASE_URL must not specify a non-standard port, received "${raw}".`
    );
  }

  if (url.search || url.hash) {
    throw new Error(
      `Production build refused: VITE_API_BASE_URL must not include a query string or fragment, received "${raw}".`
    );
  }

  if (!url.pathname.endsWith('/api')) {
    throw new Error(
      `Production build refused: VITE_API_BASE_URL must end with /api, received "${raw}". ` +
        'Without the /api prefix every request would hit the wrong backend path and return 404.'
    );
  }

  return `${url.origin}${url.pathname}`;
}
