import { describe, expect, it, vi } from 'vitest';
import {
  getApiErrorMessage,
  isTransientError,
  retryOnTransient,
} from '../services/apiErrors';

const httpError = (status, data) => ({
  response: { status, data },
  config: { url: '/auth/forgot-password' },
});

describe('isTransientError', () => {
  it.each([502, 503, 504])('treats HTTP %i as transient', (status) => {
    expect(isTransientError(httpError(status))).toBe(true);
  });

  it.each([400, 401, 403, 404, 409, 422, 429, 500])(
    'does not treat HTTP %i as transient',
    (status) => {
      expect(isTransientError(httpError(status))).toBe(false);
    }
  );

  it('treats Axios timeout and network failures as transient', () => {
    expect(isTransientError({ code: 'ECONNABORTED', message: 'timeout of 45000ms exceeded' })).toBe(
      true
    );
    expect(isTransientError({ code: 'ERR_NETWORK', message: 'Network Error' })).toBe(true);
    expect(isTransientError(new Error('Network Error'))).toBe(true);
  });

  it('ignores empty errors', () => {
    expect(isTransientError(null)).toBe(false);
    expect(isTransientError(undefined)).toBe(false);
  });
});

describe('getApiErrorMessage', () => {
  it('explains cold starts for 503/502/504 and timeouts', () => {
    for (const error of [
      httpError(503),
      httpError(502),
      httpError(504),
      { code: 'ECONNABORTED', message: 'timeout of 45000ms exceeded' },
    ]) {
      expect(getApiErrorMessage(error)).toMatch(/starting up or temporarily unavailable/i);
    }
  });

  it('returns a connection message when no HTTP response was received', () => {
    expect(getApiErrorMessage({ request: {}, code: 'ERR_NETWORK' })).toMatch(
      /unable to connect to autoscientist server/i
    );
  });

  it('surfaces the first field validation error for a 400 response', () => {
    const error = httpError(400, {
      success: false,
      message: 'Validation failed',
      data: { email: 'Valid email is required' },
    });
    expect(getApiErrorMessage(error)).toBe('Valid email is required');
  });

  it('renders object field errors instead of [object Object]', () => {
    const error = httpError(400, {
      success: false,
      message: 'Validation failed',
      data: { email: { message: 'Valid email is required' } },
    });
    expect(getApiErrorMessage(error)).toBe('Valid email is required');
  });

  it('surfaces a safe server message for 400 without field errors', () => {
    expect(
      getApiErrorMessage(httpError(400, { message: 'This reset link is invalid or has expired.' }))
    ).toBe('This reset link is invalid or has expired.');
  });

  it('returns a rate-limit message for 429', () => {
    expect(getApiErrorMessage(httpError(429))).toMatch(/too many requests/i);
  });

  it('returns the server message for 500 without leaking internals', () => {
    expect(getApiErrorMessage(httpError(500, { message: 'An internal server error occurred.' }))).toBe(
      'An internal server error occurred.'
    );
    expect(getApiErrorMessage(httpError(500))).toMatch(/unexpected error/i);
  });

  it('falls back when the error has no usable message', () => {
    expect(getApiErrorMessage(null, 'Could not request a password reset.')).toBe(
      'Could not request a password reset.'
    );
    expect(getApiErrorMessage({ response: { status: 400 } }, 'fallback')).toBe('fallback');
    expect(getApiErrorMessage({ response: { status: 400 }, message: 'Request failed with status code 400' }, 'fallback')).toBe('fallback');
  });
});

describe('retryOnTransient', () => {
  it('retries a cold-start failure and returns the successful result', async () => {
    const operation = vi
      .fn()
      .mockRejectedValueOnce(httpError(503))
      .mockRejectedValueOnce({ code: 'ECONNABORTED', message: 'timeout' })
      .mockResolvedValueOnce({ data: { success: true } });

    const result = await retryOnTransient(operation, {
      attempts: 3,
      baseDelayMs: 0,
      sleepFn: async () => {},
    });

    expect(result).toEqual({ data: { success: true } });
    expect(operation).toHaveBeenCalledTimes(3);
  });

  it('does not retry a non-transient HTTP error', async () => {
    const operation = vi.fn().mockRejectedValue(httpError(400, { message: 'Invalid token' }));

    await expect(
      retryOnTransient(operation, { attempts: 3, sleepFn: async () => {} })
    ).rejects.toMatchObject({ response: { status: 400 } });

    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('throws the last error after exhausting attempts', async () => {
    const operation = vi.fn().mockRejectedValue(httpError(503));

    await expect(
      retryOnTransient(operation, { attempts: 3, baseDelayMs: 0, sleepFn: async () => {} })
    ).rejects.toMatchObject({ response: { status: 503 } });

    expect(operation).toHaveBeenCalledTimes(3);
  });

  it('notifies the caller before each retry', async () => {
    const onRetry = vi.fn();
    const operation = vi
      .fn()
      .mockRejectedValueOnce(httpError(503))
      .mockResolvedValueOnce('ok');

    await retryOnTransient(operation, {
      attempts: 2,
      baseDelayMs: 0,
      sleepFn: async () => {},
      onRetry,
    });

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
