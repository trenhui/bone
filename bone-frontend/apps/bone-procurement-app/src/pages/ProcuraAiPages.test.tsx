import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AiReadiness from './AiReadiness';
import GovernanceStatus from './GovernanceStatus';
import HumanReviewConsole from './HumanReviewConsole';
import { readinessApi } from '../services/readinessApi';
import { qualificationApi } from '../services/qualificationApi';

// jsdom 的 CSS 解析器不支持 antd v5 cssinjs 注入的 linear-gradient（resolveGradient 崩溃）。
// 这里把 antd 换成轻量 DOM 桩，只验证页面状态逻辑（判词/失败文案/读写回），不验证样式。
vi.mock('antd', () => {
  const passthrough = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  const Descriptions = Object.assign(passthrough, { Item: passthrough });
  const Typography = Object.assign(passthrough, {
    Paragraph: passthrough,
    Text: passthrough,
    Title: passthrough,
  });
  return {
    Card: ({ title, children }: { title?: string; children?: React.ReactNode }) => (
      <section>
        <h2>{title}</h2>
        {children}
      </section>
    ),
    Alert: ({ message, description }: { message?: React.ReactNode; description?: React.ReactNode }) => (
      <div>
        {message}
        {description}
      </div>
    ),
    Button: ({
      children,
      onClick,
      disabled,
    }: {
      children?: React.ReactNode;
      onClick?: () => void;
      disabled?: boolean;
    }) => (
      <button onClick={onClick} disabled={disabled}>
        {children}
      </button>
    ),
    Descriptions,
    Space: passthrough,
    Tag: passthrough,
    Typography,
    Input: ({ value, onChange, placeholder }: {
      value?: string;
      onChange?: (e: { target: { value: string } }) => void;
      placeholder?: string;
    }) => (
      <input value={value} placeholder={placeholder} onChange={onChange} />
    ),
  };
});

vi.mock('../services/readinessApi', () => ({ readinessApi: { getReadiness: vi.fn() } }));
vi.mock('../services/qualificationApi', () => ({
  qualificationApi: { findOne: vi.fn(), reviewOcr: vi.fn() },
}));

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

function wrap(ui: React.ReactNode, entry = '/') {
  return render(<MemoryRouter initialEntries={[entry]}>{ui}</MemoryRouter>);
}

describe('procuraAI 三页反假绿行为', () => {
  beforeEach(() => vi.clearAllMocks());

  it('就绪度：未知判词显示原始码，不乐观映射为可用', async () => {
    (readinessApi.getReadiness as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { ...readyDto, verdict: 'SOME_UNMAPPED_CODE', verdictText: 'SOME_UNMAPPED_CODE' },
    });
    wrap(<AiReadiness />);
    await waitFor(() => {
      expect(screen.getByText(/SOME_UNMAPPED_CODE/)).toBeTruthy();
      expect(screen.getAllByText(/无法确认/).length).toBeGreaterThan(0);
    });
  });

  it('就绪度：取数失败显示无法确认，不显示 0 或成功态', async () => {
    (readinessApi.getReadiness as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('network'));
    wrap(<AiReadiness />);
    await waitFor(() => expect(screen.getByText('无法确认')).toBeTruthy());
  });

  it('治理页：false 不渲染成已启用', async () => {
    (readinessApi.getReadiness as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { ...readyDto, governanceEnabled: false, killSwitchEngaged: false },
    });
    wrap(<GovernanceStatus />);
    await waitFor(() => expect(screen.getByText('未启用')).toBeTruthy());
  });

  it('人工终决：缺租户参数 fail-closed，不发请求', () => {
    wrap(<HumanReviewConsole />);
    expect(screen.getByText(/缺少证照编号或租户上下文/)).toBeTruthy();
    expect(qualificationApi.findOne).not.toHaveBeenCalled();
  });

  it('人工终决：写回后刷新读回，AI 建议不显示为已生效', async () => {
    (qualificationApi.findOne as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        data: {
          id: 7,
          tenantId: 1,
          supplierId: 'SUP-1',
          certType: 'BUSINESS_LICENSE',
          verifyStatus: 'PENDING',
          reviewedBy: null,
          ocrResultJson: '{}',
        },
      })
      .mockResolvedValue({
        data: {
          id: 7,
          tenantId: 1,
          supplierId: 'SUP-1',
          certType: 'BUSINESS_LICENSE',
          verifyStatus: 'VERIFIED',
          reviewedBy: 'alice',
          ocrResultJson: '{}',
        },
      });
    (qualificationApi.reviewOcr as ReturnType<typeof vi.fn>).mockResolvedValue({ data: null });
    wrap(<HumanReviewConsole />, '/?qualificationId=7&tenantId=1');

    await waitFor(() => {
      expect(screen.getByText('待人工确认（不作自动结论）')).toBeTruthy();
      expect(screen.getByText('PENDING')).toBeTruthy();
    });
    fireEvent.click(screen.getByRole('button', { name: '确认 OCR 准确' }));

    await waitFor(() => {
      expect(qualificationApi.reviewOcr).toHaveBeenCalledWith('7', { accurate: true });
      expect(screen.getByText('VERIFIED')).toBeTruthy();
      expect(screen.getByText('alice')).toBeTruthy();
    });
  });
});
