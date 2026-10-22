import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  checkSchemaVersion,
  evalCond,
  linkageState,
  resolveVariant,
  resolveVisibleActions,
  runSubmit,
  type BoneUiSchema,
} from '@bone/ui-schema';

/**
 * 双场景串联验证（技术方案 P0-1 验收）：
 * 读 `doc/design/lowcode/cases/ui-schema-samples/` 下的真实样板（非内联重写），
 * 以员工端视角跑完整 walkthrough——样板改了本文件即回归哨兵。
 *
 * 路径用 `process.cwd()` + 仓库相对路径解析，避開 Vite 把 `import.meta.url`
 * 改写为 `/@fs/` 后 readFileSync 被 fs allowlist 拦截的坑（ENOENT）。
 * vitest 工作目录为 `bone-frontend`，故 `../doc` 即仓库根的 `doc`。
 */
function loadSample(name: string): BoneUiSchema {
  const p = resolve(process.cwd(), '../doc/design/lowcode/cases/ui-schema-samples', name);
  return JSON.parse(readFileSync(p, 'utf-8')) as unknown as BoneUiSchema;
}

const expense = loadSample('expense-claim-ui-schema.example.json');
const claim = loadSample('claim-outpatient-fasttrack-ui-schema.example.json');

const expenseDoc = (over: Record<string, unknown> = {}): Record<string, unknown> => ({
  invoice_type: 'NORMAL',
  tax_no: '',
  amount: 128.5,
  approver_id: '',
  expense_start: '2026-10-08',
  expense_end: '2026-10-10',
  status: 'DRAFT',
  items: [
    { expense_date: '2026-10-08', category_id: 'TRAVEL', amount: 88.5 },
    { expense_date: '2026-10-09', category_id: 'MEAL', amount: 40.0 },
  ],
  ...over,
});

describe('报销员工端（expense-claim 样板直连）', () => {
  it('样板结构完整：版本 ok + 表单分区 + 三规则三校验四动作', () => {
    expect(checkSchemaVersion(expense)).toBe('ok');
    expect(expense.views.form?.sections.length).toBeGreaterThan(0);
    expect(expense.linkageRules).toHaveLength(3);
    expect(expense.submitRules).toHaveLength(3);
    expect(expense.actionBar?.map((a) => a.actionCode)).toEqual(['submit', 'approve', 'reject', 'confirmPayout']);
  });

  it('列表 tabs 用标准上下文变量（$me/$all，无方言）', () => {
    const values = expense.views.list?.tabs?.map((t) => t.value) ?? [];
    expect(values).toContain('$me');
    expect(values).toContain('$all');
  });

  it('专票→税号显身必填；提交空税号被必填短路拦下', () => {
    const st = linkageState(expenseDoc({ invoice_type: 'SPECIAL' }), expense.linkageRules);
    expect(st.tax_no.visible).toBe(true);
    expect(st.tax_no.required).toBe(true);
    const r = runSubmit(expenseDoc({ invoice_type: 'SPECIAL' }), expense.submitRules, expense.linkageRules);
    expect(r.errors.some((e) => e.includes('tax_no'))).toBe(true);
  });

  it('明细合计=申报金额放行；改小申报额被 SUB 合计规则阻断', () => {
    expect(runSubmit(expenseDoc(), expense.submitRules, expense.linkageRules).errors).toHaveLength(0);
    const r = runSubmit(expenseDoc({ amount: 100 }), expense.submitRules, expense.linkageRules);
    expect(r.errors.some((e) => e.includes('明细合计一致'))).toBe(true);
  });

  it('大额 6000→审批人必填；空审批人提交阻断', () => {
    const st = linkageState(expenseDoc({ amount: 6000 }), expense.linkageRules);
    expect(st.approver_id.required).toBe(true);
    const r = runSubmit(expenseDoc({ amount: 6000 }), expense.submitRules, expense.linkageRules);
    expect(r.errors.some((e) => e.includes('approver_id'))).toBe(true);
  });

  it('结束<开始→行内 notice + 提交 compare 阻断（双轨同源）', () => {
    const doc = expenseDoc({ expense_end: '2026-10-07' });
    const st = linkageState(doc, expense.linkageRules);
    expect(st.expense_end.notice).toContain('结束日期早于开始日期');
    const r = runSubmit(doc, expense.submitRules, expense.linkageRules);
    expect(r.errors.some((e) => e.includes('结束日期不得早于开始日期'))).toBe(true);
  });

  it('动作门控：DRAFT 仅 submit；SUBMITTED 批/驳', () => {
    expect(resolveVisibleActions(expenseDoc(), expense.actionBar).map((a) => a.actionCode)).toEqual(['submit']);
    expect(
      resolveVisibleActions(expenseDoc({ status: 'SUBMITTED' }), expense.actionBar).map((a) => a.actionCode),
    ).toEqual(['approve', 'reject']);
  });
});

const claimDoc = (over: Record<string, unknown> = {}): Record<string, unknown> => ({
  status: 'DRAFT',
  claim_amount: 450,
  calc_amount: 400,
  evidences: [{ name: '门诊发票.pdf' }],
  fraud_flags: null,
  coverage_clear: true,
  invoice_amount: 400,
  items: [{ amount: 250 }, { amount: 150 }],
  reject_reasons: [],
  ...over,
});

describe('智能理赔员工端（claim fasttrack 样板直连）', () => {
  it('样板结构完整：LINK-FAST-01/DAL/EVID + SUB-CONS/COMP + reviewer 变体', () => {
    expect(checkSchemaVersion(claim)).toBe('ok');
    const codes = (claim.linkageRules ?? []).map((r) => r.code);
    expect(codes).toEqual(expect.arrayContaining(['LINK-DUAL-01', 'LINK-EVID-01', 'LINK-FAST-01']));
    expect((claim.submitRules ?? []).map((r) => r.code)).toEqual(
      expect.arrayContaining(['SUB-CONS-01', 'SUB-COMP-01']),
    );
    expect(claim.variants?.map((v) => v.key)).toContain('reviewer');
  });

  it('LINK-FAST-01：四条件全满足才亮快赔入口（all 语义，禁拆分）', () => {
    const lit = (doc: Record<string, unknown>): boolean =>
      linkageState(doc, claim.linkageRules).fasttrack_entry?.visible === true;
    expect(lit(claimDoc())).toBe(true);
    // 逐个破坏一个条件 → 全部熄灭（任一拆成独立规则都会误亮，all 才正确）
    expect(lit(claimDoc({ calc_amount: 501 }))).toBe(false);
    expect(lit(claimDoc({ evidences: [] }))).toBe(false);
    expect(lit(claimDoc({ fraud_flags: ['F1'] }))).toBe(false);
    expect(lit(claimDoc({ coverage_clear: false }))).toBe(false);
  });

  it('LINK-DUAL-01：金额≥1 万→双人复核显身必填+提示；以下不扰', () => {
    const st = linkageState(claimDoc({ claim_amount: 12000 }), claim.linkageRules);
    expect(st.second_reviewer.visible).toBe(true);
    expect(st.second_reviewer.required).toBe(true);
    expect(st.second_reviewer.notice).toContain('双人复核');
    expect(linkageState(claimDoc(), claim.linkageRules).second_reviewer).toBeUndefined();
  });

  it('LINK-EVID-01：材料缺失→补件提示+必填；有材料不扰', () => {
    const st = linkageState(claimDoc({ evidences: null }), claim.linkageRules);
    expect(st.evidences.required).toBe(true);
    expect(st.evidences.notice).toContain('补件');
  });

  it('SUB-CONS-01：发票=清单合计放行（容差 1）；差额>1 阻断', () => {
    const okDoc = (over: Record<string, unknown> = {}) =>
      claimDoc({ reject_reasons: ['r1', 'r2', 'r3'], ...over });
    expect(runSubmit(okDoc(), claim.submitRules, claim.linkageRules).errors).toHaveLength(0);
    expect(runSubmit(okDoc({ invoice_amount: 400.5 }), claim.submitRules, claim.linkageRules).errors).toHaveLength(0);
    const r = runSubmit(okDoc({ invoice_amount: 402 }), claim.submitRules, claim.linkageRules);
    expect(r.errors.some((e) => e.includes('发票金额与清单合计不一致'))).toBe(true);
  });

  it('SUB-COMP-01：拒赔完整性已加 when 门控（仅 REJECTING 生效）', () => {
    // 当前 schema 的 SUB-COMP-01 带 when: status=REJECTING，正常提交不被误拦
    expect(runSubmit(claimDoc({ reject_reasons: ['a', 'b'] }), claim.submitRules, claim.linkageRules).errors).toHaveLength(0);
    // REJECTING 且 2 条 → 阻断
    const blocked = runSubmit(claimDoc({ status: 'REJECTING', reject_reasons: ['a', 'b'] }), claim.submitRules, claim.linkageRules);
    expect(blocked.errors.some((e) => e.includes('拒赔必须≥3 条理由'))).toBe(true);
    // REJECTING 且 3 条 → 放行
    expect(runSubmit(claimDoc({ status: 'REJECTING', reject_reasons: ['a', 'b', 'c'] }), claim.submitRules, claim.linkageRules).errors).toHaveLength(0);
  });

  it('submit.when 生效前置：拒赔完整性只在 REJECTING 跑，不误伤正常提交', () => {
    // 串联验证抓到的协议缺口：SUB-COMP-01 无 when 时，员工正常提交（reject_reasons 为空）被误拦。
    // 修法：when 可选前置（schema/引擎已补），此处用叠加规则演示门控语义。
    const gated: BoneUiSchema = {
      ...claim,
      submitRules: (claim.submitRules ?? []).map((r) =>
        r.code === 'SUB-COMP-01'
          ? { ...r, when: { fieldCode: 'status', operator: 'eq', value: 'REJECTING' } }
          : r,
      ),
    };
    const pass = runSubmit(claimDoc({ reject_reasons: [] }), gated.submitRules, gated.linkageRules);
    expect(pass.errors.some((e) => e.includes('拒赔必须≥3 条理由'))).toBe(false);
    const blocked = runSubmit(
      claimDoc({ status: 'REJECTING', reject_reasons: ['a', 'b'] }),
      gated.submitRules,
      gated.linkageRules,
    );
    expect(blocked.errors.some((e) => e.includes('拒赔必须≥3 条理由'))).toBe(true);
  });

  it('reviewer 变体：核赔角色整块替换为理算工作台（sec_calc 首区）；员工回落 base', () => {
    const hit = resolveVariant(claim, { roles: ['reviewer'] });
    expect(hit.views.form?.sections[0].id).toBe('sec_calc');
    expect(resolveVariant(claim, { roles: ['employee'] })).toBe(claim);
  });

  it('动作门控：DRAFT 仅 submit；REVIEWING 批/驳/补件（approve 带双人条件原文）', () => {
    expect(resolveVisibleActions(claimDoc(), claim.actionBar).map((a) => a.actionCode)).toEqual(['submit']);
    const acts = resolveVisibleActions(claimDoc({ status: 'REVIEWING' }), claim.actionBar);
    expect(acts.map((a) => a.actionCode)).toEqual(['approve', 'reject', 'requestMoreEvidence']);
    const approve = acts.find((a) => a.actionCode === 'approve');
    expect(evalCond(approve?.requireDualApproveWhen, { claim_amount: 12000 })).toBe(true);
    expect(evalCond(approve?.requireDualApproveWhen, { claim_amount: 8000 })).toBe(false);
  });
});
