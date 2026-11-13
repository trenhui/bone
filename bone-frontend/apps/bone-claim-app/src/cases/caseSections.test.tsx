import type { ReactNode } from 'react';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const ok = (data: unknown) => Promise.resolve({ code: 200, message: 'ok', data });

// 只替换网络出口，保留六个页面真实的取数、状态、动作与受保护动作判定逻辑。
vi.mock('../services/claimApi', () => ({
  evidenceApi: { list: vi.fn(() => ok([])), register: vi.fn(), lock: vi.fn() },
  attachmentApi: { upload: vi.fn(), downloadUrl: vi.fn((ref: string) => `/files/${ref}`) },
  adjustApi: {
    latest: vi.fn(() => ok(null)), calculate: vi.fn(), recalculate: vi.fn(), confirm: vi.fn(),
  },
  settlementApi: {
    latest: vi.fn(() => ok(null)), create: vi.fn(), auditPass: vi.fn(), confirmReview: vi.fn(),
    pay: vi.fn(), cancel: vi.fn(), refund: vi.fn(),
  },
  quotaApi: { get: vi.fn(() => ok(null)) },
  appealApi: { latest: vi.fn(() => ok(null)), submit: vi.fn(), review: vi.fn() },
  claimApi: { detail: vi.fn(() => ok([])), hangUp: vi.fn() },
  inspectionApi: {
    latest: vi.fn(() => ok(null)), open: vi.fn(), claim: vi.fn(), conclude: vi.fn(),
  },
}));
vi.mock('@bone/ui', () => ({
  // 让授权按钮在测试里全部可见，负向断言才有判别力（隐藏不等于不存在）。
  AuthButton: ({
    children, onClick, disabled, title,
  }: {
    children?: ReactNode; onClick?: () => void; disabled?: boolean; title?: string;
  }) => (
    <button type="button" onClick={onClick} disabled={disabled} title={title}>{children}</button>
  ),
}));
vi.mock('@bone/ui-schema', () => ({
  SchemaRenderer: () => <div data-testid="schema-renderer" />,
  resolveVisibleActions: () => [
    { actionCode: 'execute' }, { actionCode: 'recalculate' }, { actionCode: 'confirm' },
  ],
}));

import * as claimApi from '../services/claimApi';
import { CASE_SECTIONS, EMBEDDED_CASE_TABS, isEmbeddedCaseTab } from './caseSections';
import { EvidenceUploadPage } from '../pages/EvidenceUploadPage';

const LONG_ID = '776102367333449728';

const apiSpyForTab = {
  material: () => claimApi.evidenceApi.list,
  adjust: () => claimApi.adjustApi.latest,
  fund: () => claimApi.settlementApi.latest,
  appeal: () => claimApi.appealApi.latest,
  suspend: () => claimApi.claimApi.detail,
  inspection: () => claimApi.inspectionApi.latest,
} as const;

/** 渲染无壳内容视图，并断言「无页面标题 / 无 ID 输入 / 确实按传入 claimId 取数」。 */
async function assertShellFree(tab: keyof typeof apiSpyForTab) {
  const Section = CASE_SECTIONS[tab];
  const { container } = render(
    <MemoryRouter>
      <Section claimId={LONG_ID} />
    </MemoryRouter>,
  );
  expect(container.querySelector('[data-case-content="true"]')).not.toBeNull();
  expect(container.querySelectorAll('h4.ant-typography')).toHaveLength(0);
  expect(container.querySelectorAll('input[placeholder="案件ID"]')).toHaveLength(0);
  await waitFor(() => {
    expect(apiSpyForTab[tab]()).toHaveBeenCalledWith(LONG_ID);
  });
  return container;
}

describe('案件 360 六个无壳作业视图（对象内聚）', () => {
  beforeEach(() => vi.clearAllMocks());

  it('注册表恰好六个视图，概览与队列型 trace 明确不内嵌', () => {
    expect([...EMBEDDED_CASE_TABS]).toEqual([
      'material', 'adjust', 'fund', 'appeal', 'suspend', 'inspection',
    ]);
    expect(Object.keys(CASE_SECTIONS)).toHaveLength(6);
    expect(isEmbeddedCaseTab('overview')).toBe(false);
    expect(isEmbeddedCaseTab('trace')).toBe(false);
    expect(isEmbeddedCaseTab('fund')).toBe(true);
  });

  it('2.1 材料与 OCR：内嵌态无页面标题、无 ID 输入，按 claimId 取数', async () => {
    await assertShellFree('material');
  });

  it('2.2 理算归因：内嵌态无页面标题、无 ID 输入，按 claimId 取数', async () => {
    await assertShellFree('adjust');
  });

  it('2.3 资金与结算：内嵌态逐案动作，无批量执行入口', async () => {
    const container = await assertShellFree('fund');
    expect(container.textContent).not.toMatch(/批量/);
    expect(container.textContent).toContain('额度账户');
  });

  it('2.4 申诉复议：内嵌态无页面标题、无 ID 输入，按 claimId 取数', async () => {
    await assertShellFree('appeal');
  });

  it('2.5 挂起 / 恢复：内嵌态逐案动作，无批量入口且保留 FRAUD 约束', async () => {
    const container = await assertShellFree('suspend');
    expect(container.textContent).not.toMatch(/批量/);
    expect(container.textContent).toContain('FRAUD');
  });

  it('2.6 后督质检：内嵌态逐案动作，无批量入口且保留 DIFF 逐案确认', async () => {
    const container = await assertShellFree('inspection');
    expect(container.textContent).not.toMatch(/批量/);
    expect(container.textContent).toContain('抽检');
  });

  it('旧路由页面壳仍是完整页面：PageHeader 与 ID 输入条各一处，不重复', () => {
    const { container } = render(
      <MemoryRouter initialEntries={[`/evidence?claimId=${LONG_ID}`]}>
        <EvidenceUploadPage />
      </MemoryRouter>,
    );
    expect(container.querySelectorAll('h4.ant-typography')).toHaveLength(1);
    expect(container.querySelectorAll('input[placeholder="案件ID"]')).toHaveLength(1);
    expect(container.querySelector('[data-case-content="true"]')).toBeNull();
  });
});
