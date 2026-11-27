import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import SupplierOnboarding from './SupplierOnboarding';
import { supplierRiskApi } from '../services/supplierRiskApi';

vi.mock('antd', () => {
  const box = ({ title, children }: { title?: string; children?: React.ReactNode }) => (
    <section>
      {title ? <h2>{title}</h2> : null}
      {children}
    </section>
  );
  return {
    Card: box,
    Alert: ({ message, description }: { message?: React.ReactNode; description?: React.ReactNode }) => (
      <div>{message}{description}</div>
    ),
    Button: ({ children, onClick, disabled }: { children?: React.ReactNode; onClick?: () => void; disabled?: boolean }) => (
      <button onClick={onClick} disabled={disabled}>{children}</button>
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

describe('SupplierOnboarding 准入工作台（fail-closed）', () => {
  beforeEach(() => vi.clearAllMocks());

  it('前端不发送身份字段：复核请求体只含 accurate/reason', async () => {
    vi.mocked(supplierRiskApi.reviewOcr).mockResolvedValue({ success: true, data: null, degradedReason: null });
    render(<SupplierOnboarding certExpired={false} />);
    const btn = screen.getByRole('button', { name: /复核通过/ });
    fireEvent.click(btn);
    await waitFor(() => expect(supplierRiskApi.reviewOcr).toHaveBeenCalled());
    const payload = vi.mocked(supplierRiskApi.reviewOcr).mock.calls[0][0];
    expect(Object.keys(payload).sort()).toEqual(['accurate', 'qualificationId', 'reason']);
  });

  it('过期证照不呈现复核通过，禁止人工终决', async () => {
    render(<SupplierOnboarding />);
    expect(await screen.findByText(/已过期/)).toBeTruthy();
    const btn = screen.getByRole('button', { name: /复核通过/ });
    expect(btn).toHaveProperty('disabled', true);
  });
});
