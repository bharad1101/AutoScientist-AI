import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authService } from '../services/authService';
import api from '../services/api';
import { retryOnTransient } from '../services/apiErrors';

vi.mock('../services/api', () => ({
  default: { post: vi.fn() },
}));

vi.mock('../services/apiErrors', () => ({
  getApiErrorMessage: vi.fn(),
  retryOnTransient: vi.fn((operation) => operation()),
}));

describe('authService forgot-password retry policy', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    retryOnTransient.mockImplementation((operation) => operation());
    api.post.mockResolvedValue({ data: { success: true } });
  });

  it('uses a short per-request timeout and bounded server-error-only retries', async () => {
    const result = await authService.forgotPassword('jane@university.edu');

    expect(api.post).toHaveBeenCalledWith(
      '/auth/forgot-password',
      { email: 'jane@university.edu' },
      { timeout: 10000 }
    );
    expect(retryOnTransient).toHaveBeenCalledTimes(1);

    const options = retryOnTransient.mock.calls[0][1];
    expect(options.attempts).toBe(2);
    expect(options.baseDelayMs).toBe(500);
    expect(options.maxDelayMs).toBe(1000);
    expect(options.shouldRetry({ response: { status: 503 } })).toBe(true);
    expect(options.shouldRetry({ response: { status: 502 } })).toBe(true);
    expect(options.shouldRetry({ response: { status: 504 } })).toBe(true);
    expect(options.shouldRetry({ code: 'ECONNABORTED' })).toBe(false);
    expect(options.shouldRetry({ response: { status: 500 } })).toBe(false);
    expect(result).toEqual({ success: true });
  });
});
