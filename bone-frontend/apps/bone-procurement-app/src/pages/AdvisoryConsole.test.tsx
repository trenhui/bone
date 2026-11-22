import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import AdvisoryConsole from './AdvisoryConsole';
import { advisoryApi } from '../services/advisoryApi';
import type { AdvisoryAdviceDTO } from '../types/advisoryView';

vi.mock('antd', () => {
  const passthrough = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  const Descriptions = Object.assign(passthrough, { Item: passthrough });
  const Typography = Object.assign(passthrough, { Text: passthrough, Paragraph: passthrough });
  return {
    Alert: ({ message }: { message?: React.ReactNode }) => <div>{message}</div>,
    Button: ({ children, onClick }: { children?: React.ReactNode; onClick?: () => void }) => (
      <button onClick={onClick}>{children}</button>
    ),
    Card: ({ title, children }: { title?: React.ReactNode; children?: React.ReactNode }) => (
      <section>
        <h2>{title}</h2>
        {children}
      </section>
    ),
    Descriptions,
    Select: ({
      value,
      onChange,
      options,
    }: {
      value?: string;
      onChange?: (value: string) => void;
      options?: Array<{ value: string; label: string }>;
    }) => (
      <select value={value} onChange={(e) => onChange?.(e.target.value)}>
        {(options ?? []).map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    ),
    Space: passthrough,
    Typography,
  };
});

vi.mock('../services/advisoryApi', () => ({
  advisoryApi: {
    listCapabilities: vi.fn(),
    getAdvice: vi.fn(),
  },
}));

const unknownAdvice: AdvisoryAdviceDTO = {
  capabilityCode: 'tender-health-check',
  displayName: '招标体检',
  aiAvailable: 'UNKNOWN',
  advisoryOnly: true,
  degradedTo: 'rules_only',
  explicitLabel: '规则建议（显式标识）',
  implicitMetadata: 'ai-generated=false;fallback=rules_only',
  evidenceRef: null,
  reasonCode: 'GLOBAL_KILL_SWITCH_ENGAGED',
  disclosure: 'AI 仅辅助，最终决定由人工作出',
  decidedAt: '2026-11-22T12:00:00Z',
};

describe('AI 建议态工作台', () => {
  beforeEach(() => vi.clearAllMocks());

  it('展示真实建议态与原始码，未知态不渲染成 AI 未参与', async () => {
    (advisoryApi.listCapabilities as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: ['tender-health-check'],
    });
    (advisoryApi.getAdvice as ReturnType<typeof vi.fn>).mockResolvedValue({ data: unknownAdvice });

    render(<AdvisoryConsole />);

    await waitFor(() => expect(screen.getAllByText(/UNKNOWN/).length).toBeGreaterThan(0));
    expect(screen.getAllByText(/无法确认/).length).toBeGreaterThan(0);
    expect(screen.getByText('GLOBAL_KILL_SWITCH_ENGAGED')).toBeTruthy();
    expect(screen.queryByText('AI 未参与')).toBeNull();
    expect(screen.getAllByText(/最终决定由人工作出/).length).toBeGreaterThan(0);
  });

  it('建议读取失败时显示无法确认', async () => {
    (advisoryApi.listCapabilities as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: ['tender-health-check'],
    });
    (advisoryApi.getAdvice as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('boom'));

    render(<AdvisoryConsole />);

    await waitFor(() => expect(screen.getAllByText('无法确认').length).toBeGreaterThan(0));
    expect(advisoryApi.getAdvice).toHaveBeenCalledWith('tender-health-check');
  });
});
