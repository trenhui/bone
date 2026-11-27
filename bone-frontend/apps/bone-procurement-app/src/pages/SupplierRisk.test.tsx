import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import SupplierRisk from './SupplierRisk';
import { supplierRiskApi } from '../services/supplierRiskApi';

vi.mock('antd', () => {
  const box = ({ title, children }: { title?: string; children?: React.ReactNode }) => (
    <section>
      {title ? <h2>{title}</h2> : null}
      {children}
    </section>
  );
  const Table = ({ dataSource, columns }: { dataSource?: unknown[]; columns?: Array<{ title?: string; render?: (v: unknown, r: unknown) => React.ReactNode; dataIndex?: string }> }) => (
    <table><tbody>
      {(dataSource ?? []).map((row, i) => (
        <tr key={i}>{(columns ?? []).map((col, j) => (
          <td key={j}>{col.render ? col.render((row as Record<string, unknown>)[col.dataIndex ?? ''], row) : String((row as Record<string, unknown>)[col.dataIndex ?? ''] ?? '')}</td>
        ))}</tr>
      ))}
    </tbody></table>
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
    Space: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
    Tag: ({ children }: { children?: React.ReactNode }) => <span>{children}</span>,
    Typography: Object.assign(
      ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
      { Title: ({ children }: { children?: React.ReactNode }) => <h1>{children}</h1>, Text: ({ children }: { children?: React.ReactNode }) => <span>{children}</span> },
    ),
    message: { error: vi.fn(), success: vi.fn() },
  };
});

vi.mock('../services/supplierRiskApi', () => ({
  PROCUREMENT_BASE: '/api/v1/procurement',
  QUALIFICATION_PATH: '/qualifications',
  RISK_PATH: '/performance-scores',
  supplierRiskApi: {
    getPerformance: vi.fn(),
    reviewOcr: vi.fn(),
    disposeAlert: vi.fn(),
  },
}));

describe('SupplierRisk 风险看板（fail-closed）', () => {
  beforeEach(() => vi.clearAllMocks());

  it('取数失败显示无法确认，绝不显示「无风险」', async () => {
    vi.mocked(supplierRiskApi.getPerformance).mockResolvedValue({
      success: false,
      data: null,
      degradedReason: '无法确认（取数失败，不代表无风险）',
    });
    render(<SupplierRisk />);
    expect((await screen.findAllByText(/无法确认/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/^无风险$/)).toBeNull();
  });

  it('未知严重度保留原始码并转人工提示', async () => {
    vi.mocked(supplierRiskApi.getPerformance).mockResolvedValue({
      success: true,
      data: [{ supplierId: 'S1', score: '60', grade: 'WEIRD_GRADE' }],
      degradedReason: null,
    });
    render(<SupplierRisk />);
    expect(await screen.findByText(/WEIRD_GRADE/)).toBeTruthy();
    expect((await screen.findAllByText(/人工/)).length).toBeGreaterThan(0);
  });
});
