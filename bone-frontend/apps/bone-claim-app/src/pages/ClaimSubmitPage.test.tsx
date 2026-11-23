import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../services/claimApi', () => ({
  claimApi: {
    submit: vi.fn(),
    appendCase: vi.fn(),
    reportDetail: vi.fn(),
  },
}));
vi.mock('../services/claimConfigApi', () => ({
  claimConfigApi: {
    listAllFeeCategories: vi.fn(() => Promise.resolve({ code: 200, message: 'ok', data: [] })),
  },
}));

import { ClaimSubmitPage, DECISION_LATENCY_NOTICE, FAST_TRACK_NOTICE } from './ClaimSubmitPage';
import { claimApi } from '../services/claimApi';

const ok = <T,>(data: T) => ({ code: 200, message: 'ok', data });

const REPORT_NO = 'RP20261122ABCDEFGH1234';

function detail(over: Record<string, unknown> = {}) {
  return {
    reportNo: REPORT_NO,
    policyNo: 'EG20260001',
    planId: '7',
    claimType: 'OUTPATIENT',
    outInsureTime: '2026-03-12T10:00:00Z',
    status: 'REPORTED',
    createdAt: '2026-11-22T10:00:00Z',
    caseCount: 1,
    allCasesFinished: false,
    cases: [],
    ...over,
  };
}

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ClaimSubmitPage />
    </MemoryRouter>,
  );
}

/**
 * 追加赔案模式的护栏。
 *
 * <p>这个页面的两种模式共用一套表单，最容易被改坏的地方是「该隐藏的字段没真隐藏」：
 * 事故维度字段（保单/计划/被保人/出险日期）在追加模式必须**不渲染**——它们带 required 校验，
 * 一旦残留，用户会在看不到这些字段的情况下被「请选择出险日期」拦住，且请求体还会带上不该传的值（后端 400）。
 */
describe('ClaimSubmitPage · 追加赔案模式', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (claimApi.reportDetail as ReturnType<typeof vi.fn>).mockResolvedValue(ok(detail()));
  });

  it('URL 带 reportNo 时切到追加模式：标题/按钮换成追加口径，并展示继承来的事故上下文', async () => {
    renderAt(`/submit?reportNo=${REPORT_NO}`);

    expect(await screen.findByText(`正在为报案 ${REPORT_NO} 追加赔案`)).toBeTruthy();
    expect(screen.getByRole('button', { name: /追加赔案/ })).toBeTruthy();
    // 事故上下文可见（只读展示），但不是可编辑字段
    expect(screen.getByText('EG20260001')).toBeTruthy();
  });

  it('追加模式不渲染保单/计划/被保人/出险日期字段（否则 required 会把用户卡死）', async () => {
    renderAt(`/submit?reportNo=${REPORT_NO}`);

    await screen.findByText(`正在为报案 ${REPORT_NO} 追加赔案`);

    expect(screen.queryByPlaceholderText('EG20260001')).toBeNull();
    expect(screen.queryByPlaceholderText('hash-abc')).toBeNull();
    expect(screen.queryByText('计划ID')).toBeNull();
    expect(screen.queryByText('出险日期')).toBeNull();
    // 赔付维度字段仍然要有
    expect(screen.getByPlaceholderText('04400001')).toBeTruthy();
    expect(screen.getByPlaceholderText('12345678')).toBeTruthy();
  });

  it('报案已关闭时提交按钮禁用（关闭 = 不再受理新赔付）', async () => {
    (claimApi.reportDetail as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok(detail({ status: 'CLOSED' })),
    );

    renderAt(`/submit?reportNo=${REPORT_NO}`);

    const button = await screen.findByRole('button', { name: /追加赔案/ });
    expect(button.hasAttribute('disabled')).toBe(true);
  });

  it('提交走 appendCase 而不是 submit，且请求体不含保单维度字段', async () => {
    (claimApi.appendCase as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok({ claimId: '1002', claimNo: 'CL20261122NEW', reportNo: REPORT_NO, status: 'PENDING_EVIDENCE' }),
    );

    renderAt(`/submit?reportNo=${REPORT_NO}`);
    await screen.findByText(`正在为报案 ${REPORT_NO} 追加赔案`);

    fireEvent.change(screen.getByPlaceholderText('诊查费'), { target: { value: '诊查费' } });
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '10' } });
    fireEvent.click(screen.getByRole('button', { name: /追加赔案/ }));

    await waitFor(() => expect(claimApi.appendCase).toHaveBeenCalledTimes(1));

    const [passedReportNo, passedReq] = (claimApi.appendCase as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(passedReportNo).toBe(REPORT_NO);
    // 关键：事故维度字段一律不出现在请求体里（传了会被后端 400 SC_CLAIM_REPORT_CONTEXT_MISMATCH）
    expect(passedReq).not.toHaveProperty('policyNo');
    expect(passedReq).not.toHaveProperty('insurerPolicyNo');
    expect(passedReq).not.toHaveProperty('planId');
    expect(passedReq).not.toHaveProperty('insuredIdHash');
    expect(passedReq).not.toHaveProperty('outInsureTime');
    expect(passedReq.items).toHaveLength(1);
    // 报案通道一次都不能被调用
    expect(claimApi.submit).not.toHaveBeenCalled();
  });

  it('报案号查不到时给出可定位的提示，并禁用提交', async () => {
    const err = Object.assign(new Error('not found'), { errorCode: 'SC_CLAIM_REPORT_NOT_FOUND' });
    (claimApi.reportDetail as ReturnType<typeof vi.fn>).mockRejectedValue(err);

    renderAt('/submit?reportNo=RP-INVALID');

    expect(await screen.findByText(/报案不存在或无权查看/)).toBeTruthy();
    const button = await screen.findByRole('button', { name: /追加赔案/ });
    expect(button.hasAttribute('disabled')).toBe(true);
  });
});

describe('ClaimSubmitPage · 报案模式不变', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('无 reportNo 时是报案模式：保单字段可编辑，按钮为「提交理赔」，不请求报案详情', async () => {
    renderAt('/submit');

    expect(screen.getByPlaceholderText('EG20260001')).toBeTruthy();
    expect(screen.getByPlaceholderText('hash-abc')).toBeTruthy();
    expect(screen.getByRole('button', { name: /提交理赔/ })).toBeTruthy();
    expect(claimApi.reportDetail).not.toHaveBeenCalled();
  });
});

/**
 * 高风险口径护栏（诊断报告 §7 P0-1 / §9.3）：
 * 前端不得硬编码后端不存在的快赔金额阈值或确定性时效承诺——
 * 真实金额阈值在服务端 autoAmountLimit 策略配置、按租户生效；30 秒是"建议返回 P95 ≤30s"目标。
 * 这两条曾在代码里被写死成"≤500 元可自动通过""30 秒内返回"的确定性承诺。
 */
describe('ClaimSubmitPage · 高风险口径不硬编码', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('报案页不写死 ≤500 快赔金额，改为服务端策略口径', async () => {
    renderAt('/submit');
    await screen.findByPlaceholderText('EG20260001');

    // 不得出现与后端 autoAmountLimit 冲突的硬编码金额承诺
    expect(screen.queryByText(/≤\s*500\s*元/)).toBeNull();
    expect(screen.queryByText(/500\s*元.*自动通过/)).toBeNull();
    // 必须明示阈值由服务端配置、前端不预设
    expect(screen.getByText(/金额阈值由服务端策略配置/)).toBeTruthy();
    expect(screen.getByText(/不预设具体金额/)).toBeTruthy();
  });

  it('报案成功提示常量不承诺确定 30 秒，标注 P95 目标与非时效承诺', () => {
    // 直接钉住导出口径常量（单一真源），无需穿透 antd DatePicker/表单校验
    expect(DECISION_LATENCY_NOTICE).not.toMatch(/将在 30 秒内返回/);
    expect(DECISION_LATENCY_NOTICE).toMatch(/目标 P95 30 秒内返回/);
    expect(DECISION_LATENCY_NOTICE).toMatch(/非时效承诺/);
  });

  it('快赔提示常量本身也不含硬编码金额', () => {
    expect(FAST_TRACK_NOTICE).not.toMatch(/≤\s*500|500\s*元/);
    expect(FAST_TRACK_NOTICE).toMatch(/金额阈值由服务端策略配置/);
    expect(FAST_TRACK_NOTICE).toMatch(/不预设具体金额/);
  });
});
