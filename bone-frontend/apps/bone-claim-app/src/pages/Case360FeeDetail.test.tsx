import type { ReactNode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { ClaimWithItemsProjection } from '../services/types';

const ok = (data: unknown) => Promise.resolve({ code: 200, message: 'ok', data });

const feeRow: ClaimWithItemsProjection = {
  claimId: '930001', claimNo: 'CL-001', reportId: '940001', reportNo: 'RP-001', policyNo: 'P-001', planId: 'PLAN-001',
  insuredIdHash: 'hash', consentRecordId: null, consentWithdrawnFlag: 0,
  claimType: ' outpatient', status: 'FINISHED', externalStatus: 'FINISHED', stage: 'FINISH',
  hangUpStatus: 0, invoiceCode: 'TEST', invoiceNo: '001', outInsureTime: '2026-09-09',
  sourceChannel: 'app', claimAmount: 309.98, approvedAmount: 131.94,
  firstReviewerId: '', secondReviewerId: '', rejectComment: '', rejectType: '',
  planVersion: 'v1', createdAt: '2026-09-09T00:00:00Z',
  itemId: '931001', itemName: '心电图检查', itemCode: 'B058', itemCategory: '检查' as ClaimWithItemsProjection['itemCategory'],
  itemQuantity: 2, itemUnitPrice: 154.99, itemAmount: 309.98, itemValidAmount: 131.94,
  itemFeeCode: 'F-B058', itemSocialFlag: 'Y', itemMedicalType: '甲类',
  itemPoolingAmount: 83.76, itemSelfPay1: 48.18, itemSelfPay2: 0,
  itemSelfPayC: 0, itemThirdPartyPaid: 131.94, itemInvalidAmount: 178.04,
  invoiceDate: '2026-09-08', hospitalCode: 'H001', diagnosisCode: 'D001', itemDepartment: '心内科',
};

vi.mock('../services/claimApi', () => ({
  claimApi: {
    detail: vi.fn(() => ok([feeRow])),
    hangUp: vi.fn(),
    revokeConsent: vi.fn(),
  },
  evidenceApi: { list: vi.fn(() => ok([])), register: vi.fn(), lock: vi.fn() },
  attachmentApi: { upload: vi.fn(), downloadUrl: vi.fn((ref: string) => `/files/${ref}`) },
  adjustApi: { latest: vi.fn(() => ok(null)), calculate: vi.fn(), recalculate: vi.fn(), confirm: vi.fn() },
  settlementApi: { latest: vi.fn(() => ok(null)), create: vi.fn(), auditPass: vi.fn(), confirmReview: vi.fn(), pay: vi.fn(), cancel: vi.fn(), refund: vi.fn() },
  quotaApi: { get: vi.fn(() => ok(null)) },
  appealApi: { latest: vi.fn(() => ok(null)), submit: vi.fn(), review: vi.fn() },
  inspectionApi: { latest: vi.fn(() => ok(null)), open: vi.fn(), claim: vi.fn(), conclude: vi.fn() },
  inspectionHandoffApi: { samplingConfig: vi.fn(() => ok(null)) },
}));
vi.mock('../services/aiFeedback', () => ({
  claimSearchApi: { search: vi.fn(() => ok([])) },
}));
vi.mock('@bone/ui', () => ({
  AuthButton: ({ children, onClick, disabled, title }: {
    children?: ReactNode; onClick?: () => void; disabled?: boolean; title?: string;
  }) => <button type="button" onClick={onClick} disabled={disabled} title={title}>{children}</button>,
}));
vi.mock('@bone/ui-schema', () => ({
  SchemaRenderer: () => <div data-testid="schema-renderer" />,
  resolveVisibleActions: () => [],
}));
vi.mock('../components/AiSuggestionCard', () => ({
  AiSuggestionCard: () => <div data-testid="ai-suggestion" />,
}));

import { Case360Page } from './Case360Page';

const renderOverview = () =>
  render(
    <MemoryRouter initialEntries={['/sclaim/claim/case360?claimId=930001&tab=overview']}>
      <Case360Page />
    </MemoryRouter>,
  );

describe('Case360 费用明细：后端已返回的医保结算事实字段应上屏', () => {
  it('展示医保结算分组与结算/就诊关键列（此前 types 未声明、表格未渲染）', async () => {
    renderOverview();
    await waitFor(() => {
      expect(screen.getByText('心电图检查')).toBeTruthy();
    });
    // 两级表头分组标题（antd 叶列标题可能在 DOM 中渲染多份，用 getAllByText）
    expect(screen.getAllByText('医保结算（事实记录）').length).toBeGreaterThan(0);
    expect(screen.getAllByText('就诊上下文').length).toBeGreaterThan(0);
    // 关键列标签
    for (const label of ['统筹支付', '自付一', '第三方已赔付', '不合理费用(核减)', '费用编码', '诊断编码']) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }
  });

  it('结算金额为真实值且走 MoneyText，核减列用核减口径标签', async () => {
    renderOverview();
    await waitFor(() => {
      expect(screen.getByText('心电图检查')).toBeTruthy();
    });
    // 造数真实值 83.76 / 48.18 / 178.04（非 0 占位）
    for (const amount of ['¥83.76', '¥48.18', '¥178.04']) {
      expect(screen.getAllByText(amount).length).toBeGreaterThan(0);
    }
    // "不合理费用(核减)" 标签明确语义，避免被误读为可加总赔付项
    expect(screen.getAllByText('不合理费用(核减)').length).toBeGreaterThan(0);
  });

  it('合计行明示结算列为事实记录、不参与合计，避免双扣误导', async () => {
    renderOverview();
    await waitFor(() => {
      expect(screen.getByText(/不参与此处合计/)).toBeTruthy();
    });
    expect(screen.getByText(/避免与赔付金额双扣/)).toBeTruthy();
  });
});
