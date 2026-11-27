import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import GovernanceStatus from './GovernanceStatus';
import { governanceApi } from '../services/governanceApi';
import { readinessApi } from '../services/readinessApi';
import type { GovernancePostureDTO } from '../types/governancePostureView';

vi.mock('antd', () => {
  const passthrough = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  const Descriptions = Object.assign(passthrough, { Item: passthrough });
  const Typography = Object.assign(passthrough, {
    Text: passthrough,
    Paragraph: passthrough,
    Link: ({ children, href }: { children?: React.ReactNode; href?: string }) => (
      <a href={href}>{children}</a>
    ),
  });
  const Table = ({ dataSource, columns }: { dataSource?: any[]; columns?: any[] }) => (
    <div>
      {(dataSource ?? []).map((row, i) => (
        <div key={i}>
          {(columns ?? []).map((c: any, j: number) => (
            <span key={j}>{row[c.dataIndex]}</span>
          ))}
        </div>
      ))}
    </div>
  );
  return {
    Alert: ({ message }: { message?: React.ReactNode }) => <div>{message}</div>,
    Card: ({ title, children }: { title?: React.ReactNode; children?: React.ReactNode }) => (
      <section>
        <h2>{title}</h2>
        {children}
      </section>
    ),
    Descriptions,
    Space: passthrough,
    Table,
    Tag: passthrough,
    Typography,
  };
});

vi.mock('../services/governanceApi', () => ({ governanceApi: { getPosture: vi.fn() } }));
vi.mock('../services/readinessApi', () => ({ readinessApi: { getReadiness: vi.fn() } }));

const readyDto = {
  verdict: 'AVAILABLE',
  verdictText: 'AVAILABLE',
  providerConfigured: true,
  governanceEnabled: true,
  killSwitchEngaged: false,
  evalFrozen: true,
  inferenceCapabilityRegistered: true,
  deploymentGatesPassed: true,
  authorizationNotice: '门禁通过不等于授标或放款许可',
  reasons: [],
};

const posture = (overrides: Partial<GovernancePostureDTO> = {}): GovernancePostureDTO => ({
  verdict: 'ALL_CLEAR',
  capabilities: [
    {
      code: 'M1',
      displayName: '辅助评标',
      state: 'AVAILABLE',
      decisionAllowed: true,
      decisionReasonCode: 'ALLOWED_ADVISORY_ONLY',
      driftVerdict: 'NO_ACTIONABLE_SIGNAL',
      oversightVerdict: 'NO_ACTIONABLE_SIGNAL',
      requiresOwnerReview: false,
    },
  ],
  attentionCount: 0,
  undeterminedCount: 0,
  modelHealthClaim: 'NONE',
  ...overrides,
});

function wrap() {
  return render(
    <MemoryRouter>
      <GovernanceStatus />
    </MemoryRouter>,
  );
}

describe('治理态势看板（反假绿）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (readinessApi.getReadiness as ReturnType<typeof vi.fn>).mockResolvedValue({ data: readyDto });
  });

  it('未知汇总判词显示原始码而非正常', async () => {
    (governanceApi.getPosture as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: posture({ verdict: 'SOME_NEW_VERDICT' }),
    });
    wrap();
    await waitFor(() => {
      expect(screen.getByText(/SOME_NEW_VERDICT/)).toBeTruthy();
      expect(screen.queryByText('全部正常')).toBeNull();
    });
  });

  it('取数失败显示无法确认而非默认通过', async () => {
    (governanceApi.getPosture as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('network'));
    wrap();
    await waitFor(() => expect(screen.getByText('无法确认')).toBeTruthy());
    expect(screen.queryByText('全部正常')).toBeNull();
  });

  it('披露仅渲染入口，不虚构全局状态', async () => {
    (governanceApi.getPosture as ReturnType<typeof vi.fn>).mockResolvedValue({ data: posture() });
    wrap();
    await waitFor(() => {
      const link = screen.getByText('查看合同审查披露');
      expect(link.getAttribute('href')).toContain('contract-review');
    });
    expect(screen.queryByText('已披露')).toBeNull();
    expect(screen.queryByText('未披露')).toBeNull();
  });

  it('页面无写操作按钮', async () => {
    (governanceApi.getPosture as ReturnType<typeof vi.fn>).mockResolvedValue({ data: posture() });
    wrap();
    await waitFor(() => expect(screen.getByText('辅助评标')).toBeTruthy());
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('就绪度取数失败也显示无法确认，不留空白卡片', async () => {
    (governanceApi.getPosture as ReturnType<typeof vi.fn>).mockResolvedValue({ data: posture() });
    (readinessApi.getReadiness as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('network'));
    wrap();
    await waitFor(() => expect(screen.getAllByText('无法确认').length).toBeGreaterThan(0));
  });

  it('全域总闸停用时分项全清不渲染成全部正常', async () => {
    (governanceApi.getPosture as ReturnType<typeof vi.fn>).mockResolvedValue({ data: posture() });
    (readinessApi.getReadiness as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { ...readyDto, killSwitchEngaged: true },
    });
    wrap();
    await waitFor(() => {
      expect(screen.getByText(/需关注/)).toBeTruthy();
      expect(screen.queryByText('全部正常')).toBeNull();
    });
  });
});
