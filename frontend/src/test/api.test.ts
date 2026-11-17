import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { toApiError } from '@/lib/api';

const axiosErr = (status: number | null, data?: unknown) => {
  const e = new AxiosError('fail');
  if (status != null) {
    e.response = { status, data, statusText: '', headers: {}, config: { headers: new AxiosHeaders() } };
  }
  return e;
};

describe('toApiError', () => {
  it('maps a network failure to a friendly message', () => {
    const e = toApiError(axiosErr(null));
    expect(e.status).toBe(0);
    expect(e.message).toMatch(/could not be reached/);
  });

  it('keeps server field errors and details', () => {
    const e = toApiError(axiosErr(400, { status: 400, message: 'Validation failed', fieldErrors: [{ field: 'estimatedLoss', message: 'Too low' }], details: ['x'] }));
    expect(e.fieldErrors.estimatedLoss).toBe('Too low');
    expect(e.details).toEqual(['x']);
  });

  it('explains a 403 without a server message', () => {
    expect(toApiError(axiosErr(403, {})).message).toMatch(/permission/);
  });
});
