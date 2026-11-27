import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import SourcingConsole from './SourcingConsole';
import { sourcingApi } from '../services/sourcingApi';

vi.mock('antd', () => {
  const box = ({ title, children }: { title?: string; children?: React.ReactNode }) => (
    <section>
      {title ? <h2>{title}</h2> : null}
      {children}
    </section>
  );
  const Table = ({ dataSource, columns }: { dataSource?: unknown[]; columns?: Array<{ title?: string; render?: (v: unknown, r: unknown) => React.ReactNode; dataIndex?: string }> }) => (
    <table>
      <tbody>
        {(dataSource ?? []).map((row, i) => (
          <tr key={i}>
            {(columns ?? []).map((col, j) => (
              <td key={j}>{col.render ? col.render((row as Record<string, unknown>)[col.dataIndex ?? ''], row) : String((row as Record<string, unknown>)[col.dataIndex ?? ''] ?? '')}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
  return {
    Card: box,
    Table,
    Alert: ({ message, description }: { message?: React.ReactNode; description?: React.ReactNode }) => (
      <div>{message}{description}</div>
    ),
    Button: ({ children, onClick }: { children?: React.ReactNode; onClick?: () => void }) => (
      <button onClick={onClick}>{children}</button>
    ),
    Tag: ({ children }: { children?: React.ReactNode }) => <span>{children}</span>,
    Space: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
    Typography: Object.assign(
      ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
      { Title: ({ children }: { children?: React.ReactNode }) => <h1>{children}</h1>, Text: ({ children }: { children?: React.ReactNode }) => <span>{children}</span> },
    ),
    message: { error: vi.fn(), success: vi.fn() },
  };
});

vi.mock('../services/sourcingApi', () => ({
  SOURCING_BASE: '/api/v1/procurement/business',
  sourcingApi: {
    getAward: vi.fn(),
    listQuotations: vi.fn(),
    submitBid: vi.fn(),
    recordQuotation: vi.fn(),
    award: vi.fn(),
  },
}));

describe('SourcingConsole 寻源工作台（fail-closed）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('404 → 显示能力未启用，不显示成功授标', async () => {
    vi.mocked(sourcingApi.getAward).mockResolvedValue({
      success: false,
      data: null,
      degradedReason: '能力未启用',
    });
    vi.mocked(sourcingApi.listQuotations).mockResolvedValue({
      success: false,
      data: null,
      degradedReason: '能力未启用',
    });
    render(<SourcingConsole />);
    expect((await screen.findAllByText(/能力未启用/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/已授标/)).toBeNull();
  });

  it('授标确认人缺失不显示人工终决成功，AI 建议只作建议', async () => {
    vi.mocked(sourcingApi.getAward).mockResolvedValue({
      success: true,
      data: { rfxNo: 'RFX-1', supplierId: 'S-1' },
      degradedReason: null,
    });
    vi.mocked(sourcingApi.listQuotations).mockResolvedValue({
      success: true,
      data: [{ quoteId: 'Q1', amount: '100', currency: 'CNY', comparable: true, aiRecommendation: '建议授标' }],
      degradedReason: null,
    });
    render(<SourcingConsole />);
    expect((await screen.findAllByText(/无法确认/)).length).toBeGreaterThan(0);
    expect(screen.getByText(/建议授标/)).toBeTruthy();
    expect(screen.queryByText(/已终决/)).toBeNull();
  });
});
