const TRANSIENT_STATUS_CODES = new Set([502, 503, 504]);

const TRANSIENT_ERROR_CODES = new Set([
  'ECONNABORTED',
  'ETIMEDOUT',
  'ERR_NETWORK',
  'ERR_CONNECTION_RESET',
  'ECONNRESET',
  'ENOTFOUND',
  'EAI_AGAIN',
]);

const COLD_START_MESSAGE =
  'The AutoScientist server is starting up or temporarily unavailable. ' +
  'Free-tier hosting can take up to a minute to wake. Please wait a moment and try again.';

const NETWORK_MESSAGE =
  'Unable to connect to AutoScientist server. Check your internet connection and try again. ' +
  'If the problem persists, the server may still be starting up (this can take up to a minute).';

/**
 * True when retrying the same request has a reasonable chance of succeeding:
 * Render free-tier cold starts return 502/503/504 or time out with no response.
 * Ordinary HTTP errors (400/401/403/404/429/500) are never retried.
 */
export function isTransientError(error) {
  if (!error) {
    return false;
  }

  const status = error.response?.status;
  if (status != null) {
    return TRANSIENT_STATUS_CODES.has(status);
  }

  if (error.code && TRANSIENT_ERROR_CODES.has(error.code)) {
    return true;
  }

  const message = typeof error.message === 'string' ? error.message : '';
  return /network error|timeout/i.test(message);
}

/**
 * Maps an Axios/fetch error to a safe, user-facing message. Never returns a
 * stack trace, raw token, credential, or internal server detail.
 */
export function getApiErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) {
    return fallback;
  }

  const status = error.response?.status;
  const data = error.response?.data;

  if (status === 429) {
    return 'Too many requests. Please wait a minute and try again.';
  }

  if (
    status === 502 ||
    status === 503 ||
    status === 504 ||
    error.code === 'ECONNABORTED' ||
    /timeout/i.test(typeof error.message === 'string' ? error.message : '')
  ) {
    return COLD_START_MESSAGE;
  }

  if (!error.response) {
    return NETWORK_MESSAGE;
  }

  if (status === 400 && data?.data && typeof data.data === 'object') {
    const fieldError = Object.values(data.data)[0];
    if (typeof fieldError === 'string' && fieldError.trim()) {
      return fieldError;
    }
    if (fieldError && typeof fieldError === 'object' && typeof fieldError.message === 'string') {
      return fieldError.message;
    }
  }

  if (status >= 500) {
    return data?.message || 'The server hit an unexpected error. Please try again shortly.';
  }

  return data?.message || fallback;
}

const defaultSleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Retries an operation only for transient/cold-start failures. Intended for
 * idempotent or safe-to-repeat calls such as requesting a reset link. Callers
 * that mutate single-use state (e.g. consuming a reset token) should not use it.
 */
export async function retryOnTransient(
  operation,
  {
    attempts = 3,
    baseDelayMs = 1500,
    maxDelayMs = 6000,
    shouldRetry = isTransientError,
    onRetry,
    sleepFn = defaultSleep,
  } = {}
) {
  let lastError;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      if (attempt >= attempts || !shouldRetry(error)) {
        throw error;
      }
      if (typeof onRetry === 'function') {
        onRetry(error, attempt);
      }
      const delay = Math.min(maxDelayMs, baseDelayMs * 2 ** (attempt - 1));
      await sleepFn(delay);
    }
  }

  throw lastError;
}
