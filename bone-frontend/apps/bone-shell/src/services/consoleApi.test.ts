import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import { fetchConsoleOverview, fetchQuickActions } from './consoleApi';

vi.mock('axios', () => ({
  default: { get: vi.fn() },
}));

describe('consoleApi', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset();
  });

  it('fetchConsoleOverview returns data when code is 200', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: {
        code: 200,
        data: { services: [{ name: 'IAM', status: 'UP' }] },
      },
    });
    const result = await fetchConsoleOverview();
    expect(axios.get).toHaveBeenCalledWith('/api/console/overview');
    expect(result?.services).toHaveLength(1);
  });

  it('fetchQuickActions returns empty array on non-200', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { code: 500, data: null } });
    const result = await fetchQuickActions();
    expect(axios.get).toHaveBeenCalledWith('/api/console/quick-actions');
    expect(result).toEqual([]);
  });
});
