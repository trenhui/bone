import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// 只替换网络服务：页面自身的 Descriptions / Table / Tag 渲染保持真实
vi.mock('../services/claimApi', () => ({
  claimApi: {
    detail: vi.fn(),
    reportDetail: vi.fn(),
  },
}));

import { ClaimProgressPage } from './ClaimProgressPage';
import { claimApi } from '../services/claimApi';

const ok = <T,>(data: T) => ({ code: 200, message: 'ok', data });

const CASE_ID = '1001';
const REPORT_NO = 'RP20261122ABCDEFGH1234';

function caseRow(over: Record<string, unknown> = {}) {
  return {
    claimId: CASE_ID,
    claimNo: 'CL20261122ABCDEFGH1234',
    reportId: '2001',
    reportNo: REPORT_NO,
    policyNo: 'EG20260001',
    planId: '7',
    insuredIdHash: 'hash-abc',
    consentRecordId: null,
    consentWithdrawnFlag: 0,
    claimType: 'OUTPATIENT',
    status: 'PENDING_ADJUST',
    externalStatus: 'PENDING_EVIDENCE',
    stage: 'AUDITING',
    hangUpStatus: 0,
    outInsureTime: '2026-03-12T10:00:00Z',
    sourceChannel: '',
    claimAmount: 100,
    approvedAmount: null,
    firstReviewerId: '',
    secondReviewerId: '',
    rejectComment: '',
    rejectType: '',
    planVersion: 'v3',
    createdAt: '2026-11-22T10:00:00Z',
    itemId: null,
    ...over,
  };
}

function reportDetail(over: Record<string, unknown> = {}) {
  return {
    reportNo: REPORT_NO,
    policyNo: 'EG20260001',
    planId: '7',
    claimType: 'OUTPATIENT',
    outInsureTime: '2026-03-12T10:00:00Z',
    status: 'REPORTED',
    createdAt: '2026-11-22T10:00:00Z',
    caseCount: 2,
    allCasesFinished: false,
    cases: [
      {
        claimId: CASE_ID,
        claimNo: 'CL20261122ABCDEFGH1234',
        status: 'PENDING_ADJUST',
        externalStatus: 'PENDING_EVIDENCE',
        stage: 'AUDITING',
        claimAmount: 100,
        approvedAmount: null,
        createdAt: '2026-11-22T10:00:00Z',
      },
      {
        claimId: '1002',
        claimNo: 'CL20261122ZZZZZZZZ9999',
        status: 'FINISHED',
        externalStatus: null,
        stage: 'FINISH',
        claimAmount: 260,
        approvedAmount: 240,
        createdAt: '2026-11-23T10:00:00Z',
      },
    ],
    ...over,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/progress?claimId=${CASE_ID}`]}>
      <ClaimProgressPage />
    </MemoryRouter>,
  );
}

/**
 * 报案 / 赔案拆分后的进度页护栏。
 *
 * <p>核心回归点：**标签与数据必须是同一个东西**。拆分前这里只有一条「报案号」，值却是 claim_no（赔案号）——
 * 用户据此向客服报「报案号」会查不到。若有人把标签改回去，或把两条都指向 claimNo，本组用例会红。
 */
describe('ClaimProgressPage · 报案号与赔案号分离', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (claimApi.detail as ReturnType<typeof vi.fn>).mockResolvedValue(ok([caseRow()]));
    (claimApi.reportDetail as ReturnType<typeof vi.fn>).mockResolvedValue(ok(reportDetail()));
  });

  it('赔案号与报案号各一条，且各自取到自己的值', async () => {
    renderPage();

    const claimNo = await screen.findByText('CL20261122ABCDEFGH1234');
    expect(claimNo).toBeTruthy();

    // 「报案号」只出现一次（纠错前它指向 claimNo，与「赔案号」是同一个值）。
    // 「赔案号」会出现两次：案件信息区一次 + 同案清单列头一次——两处都该叫赔案号，
    // 所以这里不做等值断言，真正的判别力在下面「各自取到自己的值」。
    expect(screen.getAllByText('报案号')).toHaveLength(1);
    expect(screen.getAllByText('赔案号').length).toBeGreaterThanOrEqual(1);

    // 报案号取的是 reportNo，不是 claimNo
    expect(screen.getByText(REPORT_NO)).toBeTruthy();
  });

  it('报案号缺失时显示「未关联」而不是冒充赔案号', async () => {
    (claimApi.detail as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok([caseRow({ reportId: null, reportNo: null })]),
    );

    renderPage();

    expect(await screen.findByText('未关联（历史数据未回填）')).toBeTruthy();
    // 没有报案号就不该去请求报案详情
    expect(claimApi.reportDetail).not.toHaveBeenCalled();
  });
});

describe('ClaimProgressPage · 同案赔案清单', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (claimApi.detail as ReturnType<typeof vi.fn>).mockResolvedValue(ok([caseRow()]));
    (claimApi.reportDetail as ReturnType<typeof vi.fn>).mockResolvedValue(ok(reportDetail()));
  });

  it('按报案号拉同案清单，并列出每一笔赔案', async () => {
    renderPage();

    expect(await screen.findByText('同案赔案（2 笔）')).toBeTruthy();
    expect(claimApi.reportDetail).toHaveBeenCalledWith(REPORT_NO);

    // 同案清单里另一笔赔案必须可见（这是「一次事故多笔赔付」在界面上的唯一落点）
    const other = await screen.findByText('CL20261122ZZZZZZZZ9999');
    const table = other.closest('table');
    expect(table).toBeTruthy();
    expect(within(table as HTMLElement).getByText('已结案')).toBeTruthy();
  });

  it('报案开放中时「追加赔案」可点击', async () => {
    renderPage();

    const button = await screen.findByRole('button', { name: /追加赔案/ });
    expect(button.hasAttribute('disabled')).toBe(false);
  });

  it('报案已关闭时「追加赔案」被禁用（关闭=不再受理新赔付，不影响在办案件）', async () => {
    (claimApi.reportDetail as ReturnType<typeof vi.fn>).mockResolvedValue(
      ok(reportDetail({ status: 'CLOSED' })),
    );

    renderPage();

    expect(await screen.findByText('报案已关闭')).toBeTruthy();
    const button = await screen.findByRole('button', { name: /追加赔案/ });
    expect(button.hasAttribute('disabled')).toBe(true);
  });

  it('报案详情拉取失败时降级为空态，不影响赔案本身展示', async () => {
    (claimApi.reportDetail as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('boom'));

    renderPage();

    expect(await screen.findByText(/未取到同案赔案清单/)).toBeTruthy();
    // 主数据（赔案号）仍然可见
    expect(screen.getByText('CL20261122ABCDEFGH1234')).toBeTruthy();
  });
});
