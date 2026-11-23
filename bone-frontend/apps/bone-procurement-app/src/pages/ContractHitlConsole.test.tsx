import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ContractHitlConsole from './ContractHitlConsole';
import { contractReviewApi } from '../services/contractReviewApi';
import type { ContractReviewReportDTO } from '../types/contractHitlView';

// jsdom 不解析 antd v5 cssinjs gradient；这里只做 DOM 桩，验证状态与写回 payload。
vi.mock('antd', () => {
  const passthrough = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  const Descriptions = Object.assign(passthrough, { Item: passthrough });
  const Typography = Object.assign(passthrough, {
    Paragraph: passthrough,
    Text: passthrough,
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
    Typography,
    Input: Object.assign(
      ({ value, onChange, placeholder }: {
        value?: string;
        onChange?: (e: { target: { value: string } }) => void;
        placeholder?: string;
      }) => <input value={value} placeholder={placeholder} onChange={onChange} />,
      {
        TextArea: ({ value, onChange, placeholder }: {
          value?: string;
          onChange?: (e: { target: { value: string } }) => void;
          placeholder?: string;
        }) => <textarea value={value} placeholder={placeholder} onChange={onChange} />,
      },
    ),
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
  };
});

vi.mock('../services/contractReviewApi', () => ({
  contractReviewApi: {
    getReport: vi.fn(),
    getDisclosure: vi.fn(),
    override: vi.fn(),
  },
}));

const pending: ContractReviewReportDTO = {
  reviewId: 'rv-1',
  contractNo: 'CT-1',
  status: 'HUMAN_REVIEW',
  aiAvailable: 'AVAILABLE',
  riskLevel: 'LOW',
  humanFinalized: false,
  humanReason: null,
};
const finalized: ContractReviewReportDTO = {
  ...pending,
  status: 'SNAPSHOTTED',
  humanFinalized: true,
  humanReason: '人工判定为高风险',
};
const disclosure = {
  reviewId: 'rv-1',
  aiAvailableCode: 'AVAILABLE',
  disclosureText: 'AI 已参与',
  canConfirm: true,
  modelVersionSnapshot: 'm1',
};

function wrap(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <ContractHitlConsole />
    </MemoryRouter>,
  );
}

describe('合同人工终决工作台', () => {
  beforeEach(() => vi.clearAllMocks());

  it('缺少 reviewId 时 fail-closed，不发任何请求', () => {
    wrap('/contract-hitl');
    expect(screen.getByText(/缺少审查编号，无法终决/)).toBeTruthy();
    expect(contractReviewApi.getReport).not.toHaveBeenCalled();
    expect(contractReviewApi.override).not.toHaveBeenCalled();
  });

  it('写回 payload 不含自报复核人，并在写回后刷新读回', async () => {
    (contractReviewApi.getReport as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ data: pending })
      .mockResolvedValueOnce({ data: finalized });
    (contractReviewApi.getDisclosure as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: disclosure,
    });
    (contractReviewApi.override as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: finalized,
    });

    wrap('/contract-hitl?reviewId=rv-1');
    await waitFor(() => expect(screen.getByText('待人工终决')).toBeTruthy());

    fireEvent.change(screen.getByPlaceholderText('请填写人工终决理由'), {
      target: { value: '人工判定为高风险' },
    });
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'HIGH' } });
    fireEvent.click(screen.getByRole('button', { name: '提交人工终决' }));

    await waitFor(() =>
      expect(contractReviewApi.override).toHaveBeenCalledWith('rv-1', {
        reason: '人工判定为高风险',
        finalRiskLevel: 'HIGH',
      }),
    );
    await waitFor(() => expect(screen.getAllByText('已终决').length).toBeGreaterThan(0));
    expect((contractReviewApi.getReport as ReturnType<typeof vi.fn>).mock.calls.length).toBe(2);
  });

  it('已终决报告不显示可提交按钮', async () => {
    (contractReviewApi.getReport as ReturnType<typeof vi.fn>).mockResolvedValue({ data: finalized });
    (contractReviewApi.getDisclosure as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: disclosure,
    });

    wrap('/contract-hitl?reviewId=rv-1');
    await waitFor(() => expect(screen.getAllByText('已终决').length).toBeGreaterThan(0));
    expect(screen.queryByRole('button', { name: '提交人工终决' })).toBeNull();
    expect(contractReviewApi.override).not.toHaveBeenCalled();
  });
});
