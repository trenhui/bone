import { describe, expect, it } from 'vitest';
import {
  checkSchemaVersion,
  controlBaseStates,
  evalCond,
  fieldVisible,
  linkageState,
  pathVal,
  resolveVariant,
  resolveVisibleActions,
  rolesVisible,
  runSubmit,
  validateControlValues,
  type ActionBarItem,
  type BoneUiSchema,
  type UiCondition,
  type Control,
  type LinkageRule,
  type Section,
  type SubmitRule,
} from '@bone/ui-schema';
import { lintSchema, type LintIssue } from '../components/uiSchema/lint';

/* 报销实例（与 expense-claim-ui-schema.example.json 同源，内联避免 JSON 模块配置依赖） */
const linkageRules: LinkageRule[] = [
  {
    name: '专票必填税号',
    when: { fieldCode: 'invoice_type', operator: 'eq', value: 'SPECIAL' },
    then: [{ fieldCode: 'tax_no', visible: true, required: true }],
  },
  {
    name: '结束日期不得早于开始日期',
    when: { fieldCode: 'expense_end', operator: 'earlierThan', valueType: 'field', compareField: 'expense_start' },
    then: [{ fieldCode: 'expense_end', readonly: false, notice: '结束日期早于开始日期，请修正（提交时阻断）' }],
  },
  {
    name: '大额报销必须指定审批人',
    when: { fieldCode: 'amount', operator: 'gt', value: 5000 },
    then: [{ fieldCode: 'approver_id', visible: true, required: true }],
  },
];

const submitRules: SubmitRule[] = [
  {
    name: '明细合计一致', function: 'sum', fields: ['items.amount'], operator: 'eq',
    compare: { valueType: 'field', fieldCode: 'amount' }, verifyType: 'ERROR', message: '费用明细合计必须等于申报金额',
  },
  {
    name: '单行金额下限', function: 'min', fields: ['items.amount'], operator: 'gte',
    compare: { valueType: 'fixed', value: 0.01 }, verifyType: 'ERROR', message: '费用明细单行金额不得小于 0.01',
  },
  {
    name: '结束日期不得早于开始日期', function: 'compare', fields: ['expense_end'], operator: 'gte',
    compare: { valueType: 'field', fieldCode: 'expense_start' }, verifyType: 'ERROR', message: '结束日期不得早于开始日期',
  },
];

const actionBar: ActionBarItem[] = [
  { actionCode: 'submit', visibleWhen: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, sortOrder: 1 },
  { actionCode: 'approve', visibleWhen: { fieldCode: 'status', operator: 'eq', value: 'SUBMITTED' }, sortOrder: 2 },
  { actionCode: 'reject', visibleWhen: { fieldCode: 'status', operator: 'eq', value: 'SUBMITTED' }, sortOrder: 3 },
  { actionCode: 'confirmPayout', visibleWhen: { fieldCode: 'status', operator: 'eq', value: 'APPROVED' }, sortOrder: 4 },
];

function baseDoc(over: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    invoice_type: 'NORMAL', tax_no: '', amount: 128.5, approver_id: '',
    expense_start: '2026-10-08', expense_end: '2026-10-10', status: 'DRAFT',
    items: [
      { expense_date: '2026-10-08', category_id: 'TRAVEL', amount: 88.5, remark: '机票' },
      { expense_date: '2026-10-09', category_id: 'MEAL', amount: 40.0, remark: '客户餐' },
    ],
    attachment: [{ name: '发票-1008.pdf' }],
    ...over,
  };
}

describe('pathVal 路径求值', () => {
  it('穿透数组：items.amount → 各行列值数组', () => {
    const v = pathVal(baseDoc(), 'items.amount');
    expect(v).toEqual([88.5, 40]);
  });
  it('单值路径返回标量', () => {
    expect(pathVal(baseDoc(), 'amount')).toBe(128.5);
  });
  it('缺失路径返回 undefined', () => {
    expect(pathVal(baseDoc(), 'nope.x')).toBeUndefined();
  });
});

describe('evalCond 条件引擎', () => {
  it('原子 eq 命中/不命中', () => {
    expect(evalCond({ fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, baseDoc())).toBe(true);
    expect(evalCond({ fieldCode: 'status', operator: 'eq', value: 'PAID' }, baseDoc())).toBe(false);
  });
  it('all/any/not 组合', () => {
    const c: UiCondition = { all: [{ fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, { fieldCode: 'amount', operator: 'gt', value: 100 }] };
    expect(evalCond(c, baseDoc())).toBe(true);
    expect(evalCond(c, baseDoc({ amount: 50 }))).toBe(false);
    expect(evalCond({ any: [{ fieldCode: 'status', operator: 'eq', value: 'X' }, { fieldCode: 'amount', operator: 'gt', value: 100 }] }, baseDoc())).toBe(true);
    expect(evalCond({ not: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' } }, baseDoc())).toBe(false);
  });
  it('valueType=field 比较两字段', () => {
    expect(evalCond({ fieldCode: 'expense_end', operator: 'earlierThan', valueType: 'field', compareField: 'expense_start' }, baseDoc())).toBe(false);
    expect(evalCond({ fieldCode: 'expense_end', operator: 'earlierThan', valueType: 'field', compareField: 'expense_start' }, baseDoc({ expense_end: '2026-10-07' }))).toBe(true);
  });
  it('上下文变量 $me 解析', () => {
    expect(evalCond({ fieldCode: 'applicant', operator: 'eq', value: '$me' }, { applicant: '张三' }, { me: '张三' })).toBe(true);
    expect(evalCond({ fieldCode: 'applicant', operator: 'eq', value: '$me' }, { applicant: '李四' }, { me: '张三' })).toBe(false);
  });
});

describe('linkageState 联动', () => {
  it('默认态：tax_no 隐藏、approver_id 非必填', () => {
    // tax_no 基线隐藏（专票才现身）—— 字段基线态来自控件声明
    // 缺省兜底：未触及字段 = 默认态（visible, 非必填），与渲染器消费契约一致
    const def = (s: ReturnType<typeof linkageState>, f: string) => s[f] ?? { visible: true, required: false, readonly: false, disabled: false, notice: '' };
    const st = linkageState(baseDoc(), linkageRules, {}, {}, { tax_no: { visible: false } });
    expect(st.tax_no.visible).toBe(false);
    expect(st.tax_no.required).toBe(false);
    expect(def(st, 'approver_id').required).toBe(false);
  });
  it('专票 → tax_no 显身且必填', () => {
    const st = linkageState(baseDoc({ invoice_type: 'SPECIAL' }), linkageRules);
    expect(st.tax_no.visible).toBe(true);
    expect(st.tax_no.required).toBe(true);
  });
  it('金额>5000 → 审批人显身且必填', () => {
    const st = linkageState(baseDoc({ amount: 6000 }), linkageRules);
    expect(st.approver_id.visible).toBe(true);
    expect(st.approver_id.required).toBe(true);
  });
  it('结束早于开始 → 行内 notice', () => {
    const st = linkageState(baseDoc({ expense_end: '2026-10-07' }), linkageRules);
    expect(st.expense_end.notice).toContain('结束日期早于开始日期');
  });
});

describe('runSubmit 提交校验', () => {
  it('明细合计=申报金额 → 通过', () => {
    const r = runSubmit(baseDoc(), submitRules, linkageRules);
    expect(r.errors).toHaveLength(0);
  });
  it('明细合计≠申报金额 → ERROR 阻断', () => {
    const r = runSubmit(baseDoc({ amount: 100 }), submitRules, linkageRules);
    expect(r.errors.some((e) => e.includes('明细合计一致'))).toBe(true);
  });
  it('单行金额下限 → 通过', () => {
    const r = runSubmit(baseDoc(), submitRules, linkageRules);
    expect(r.errors.some((e) => e.includes('单行金额下限'))).toBe(false);
  });
  it('存在 <0.01 的明细行 → ERROR', () => {
    const doc = baseDoc({ items: [{ amount: 0.005 }, { amount: 40 }] });
    const r = runSubmit(doc, submitRules, linkageRules);
    expect(r.errors.some((e) => e.includes('单行金额下限'))).toBe(true);
  });
  it('结束≥开始 → 通过；结束<开始 → ERROR', () => {
    expect(runSubmit(baseDoc(), submitRules, linkageRules).errors.some((e) => e.includes('结束日期不得早于开始'))).toBe(false);
    const bad = runSubmit(baseDoc({ expense_end: '2026-10-07' }), submitRules, linkageRules);
    expect(bad.errors.some((e) => e.includes('结束日期不得早于开始'))).toBe(true);
  });
  it('必填短路：专票但税号为空 → ERROR', () => {
    const r = runSubmit(baseDoc({ invoice_type: 'SPECIAL', tax_no: '' }), submitRules, linkageRules);
    expect(r.errors.some((e) => e.includes('tax_no'))).toBe(true);
  });
});

describe('resolveVisibleActions 状态机门控', () => {
  it('DRAFT → 仅 submit', () => {
    const a = resolveVisibleActions(baseDoc({ status: 'DRAFT' }), actionBar).map((x) => x.actionCode);
    expect(a).toEqual(['submit']);
  });
  it('SUBMITTED → approve + reject（按 sortOrder）', () => {
    const a = resolveVisibleActions(baseDoc({ status: 'SUBMITTED' }), actionBar).map((x) => x.actionCode);
    expect(a).toEqual(['approve', 'reject']);
  });
  it('APPROVED → confirmPayout', () => {
    const a = resolveVisibleActions(baseDoc({ status: 'APPROVED' }), actionBar).map((x) => x.actionCode);
    expect(a).toEqual(['confirmPayout']);
  });
});

describe('resolveVariant 变体裁决（v1.1）', () => {
  const schema: BoneUiSchema = {
    schemaVersion: 1,
    entityCode: 't_expense_reimbursement',
    views: { form: { sections: [{ id: 'base', controls: [{ fieldCode: 'title' }] }] } },
    actionBar,
    variants: [
      {
        key: 'reviewer', priority: 10, match: { roles: ['reviewer'] },
        views: { form: { sections: [{ id: 'rev', controls: [{ fieldCode: 'approver_id' }] }] } },
      },
    ],
  };
  it('命中角色变体 → 整块替换 form', () => {
    const r = resolveVariant(schema, { roles: ['reviewer'] });
    expect(r.views.form?.sections[0].id).toBe('rev');
  });
  it('未命中 → 回落 base', () => {
    const r = resolveVariant(schema, { roles: ['employee'] });
    expect(r.views.form?.sections[0].id).toBe('base');
  });
  it('缺 tier 不猜 → 回落 base', () => {
    const s2: BoneUiSchema = { ...schema, variants: [{ key: 'pro', priority: 10, match: { tiers: ['pro'] }, views: { form: { sections: [{ id: 'pro', controls: [] }] } } }] };
    expect(resolveVariant(s2, {}).views.form?.sections[0].id).toBe('base');
  });
});

describe('runSubmit v1.1 四函数（样板语义）', () => {
  const bal: SubmitRule[] = [{
    name: '借贷平衡', code: 'SUB-BAL-01', function: 'balance', fields: ['lines.debit', 'lines.credit'],
    operator: 'eq', params: { tolerance: 0.01 }, verifyType: 'ERROR', message: '借贷不平衡',
  }];
  const journal = (debit: number[], credit: number[]) => ({
    lines: debit.map((d, i) => ({ debit: d, credit: credit[i] ?? 0 })),
  });
  it('balance：借贷相等 → 通过；差额>容差 → ERROR', () => {
    expect(runSubmit(journal([100, 50], [100, 50]), bal).errors).toHaveLength(0);
    // 差额==容差算过（erp"差额>0.01 不得保存"同口径）
    expect(runSubmit(journal([100.01], [100]), bal).errors).toHaveLength(0);
    const r = runSubmit(journal([100.05], [100]), bal);
    expect(r.errors.some((e) => e.includes('借贷平衡'))).toBe(true);
  });
  it('balance：字段不足两组 → 配置错误阻断（fail-closed）', () => {
    const bad: SubmitRule[] = [{ ...bal[0], fields: ['lines.debit'] }];
    expect(runSubmit(journal([1], [1]), bad).errors.some((e) => e.includes('配置错误'))).toBe(true);
  });

  const cons: SubmitRule[] = [{
    name: '发票与清单一致', code: 'SUB-CONS-01', function: 'consistency',
    fields: ['invoice_amount', 'items.amount'], operator: 'eq',
    params: { tolerance: 1 }, verifyType: 'ERROR', message: '发票金额与清单合计不一致',
  }];
  it('consistency：发票=清单合计 → 通过；差额>容差 → ERROR', () => {
    expect(runSubmit({ ...baseDoc(), invoice_amount: 128.5 }, cons).errors).toHaveLength(0);
    expect(runSubmit({ ...baseDoc(), invoice_amount: 129 }, cons).errors).toHaveLength(0);
    const r = runSubmit({ ...baseDoc(), invoice_amount: 131 }, cons);
    expect(r.errors.some((e) => e.includes('发票与清单一致'))).toBe(true);
  });

  // 缺值不扰（与 unique 同口径）：左值未录入时不做一致性判定——「没填」不是「填错」。
  // 实测（2026-10-21 全模块联调）：理赔单 invoice_amount 未录入（也不在表单控件里）时
  // cmp('eq','',0) 恒 false ⇒ 恒定报「发票金额与清单合计不一致」，用户无论怎么填都提交不了。
  it('consistency：左值未录入 → 跳过（不把「没填」误报成「金额不一致」）', () => {
    expect(runSubmit({ ...baseDoc(), invoice_amount: '' }, cons).errors).toHaveLength(0);
    expect(runSubmit({ ...baseDoc() }, cons).errors).toHaveLength(0);
  });
  it('consistency：左值已录入仍照常校验（0 元是合法金额，不得被当成未录入）', () => {
    const zero: SubmitRule[] = [cons[0]];
    const noItems = { ...baseDoc(), invoice_amount: 0, items: [] };
    // invoice_amount=0 且清单合计 0 → 一致，通过
    expect(runSubmit(noItems, zero).errors).toHaveLength(0);
    // invoice_amount=0 但清单合计 100 → 差 100 > 容差 1 → 必须拦下（证明 0 不是「跳过」）
    const mismatch = { ...baseDoc(), invoice_amount: 0, items: [{ amount: 100 }] };
    expect(runSubmit(mismatch, zero).errors.some((e) => e.includes('发票与清单一致'))).toBe(true);
  });

  const comp: SubmitRule[] = [{
    name: '拒赔理由完整性', code: 'SUB-COMP-01', function: 'completeness', fields: ['reject_reasons'],
    operator: 'gte', params: { minReasons: 3 }, verifyType: 'ERROR', message: '拒赔必须≥3 条理由',
  }];
  it('completeness：理由数达标 → 通过；不足 → ERROR', () => {
    expect(runSubmit({ reject_reasons: ['a', 'b', 'c'] }, comp).errors).toHaveLength(0);
    const r = runSubmit({ reject_reasons: ['a', 'b'] }, comp);
    expect(r.errors.some((e) => e.includes('拒赔理由完整性'))).toBe(true);
  });
  it('completeness：缺省阈值=fields.length（六要素口径）', () => {
    const six: SubmitRule[] = [{
      name: '六要素完整性', function: 'completeness',
      fields: ['data_source', 'quality_stars', 'fair_value_band', 'growth_rate', 'risk_factors', 'track_rules'],
      operator: 'gte', verifyType: 'ERROR', message: '缺任一模板要素不得发布结论',
    }];
    const full = { data_source: 'x', quality_stars: 5, fair_value_band: 'y', growth_rate: 1, risk_factors: 'z', track_rules: 'w' };
    expect(runSubmit(full, six).errors).toHaveLength(0);
    expect(runSubmit({ ...full, track_rules: '' }, six).errors.some((e) => e.includes('六要素'))).toBe(true);
  });

  const uniq: SubmitRule[] = [{
    name: '查重提示', function: 'unique', fields: ['customer_id', 'opp_name'],
    operator: 'eq', params: { scope: 'team' }, verifyType: 'WARNING', message: '疑似重复创建',
  }];
  it('unique：标量组合全非空 → WARNING 提醒（前端交人工确认）；缺值不扰', () => {
    const r = runSubmit({ customer_id: 'c1', opp_name: 'o1' }, uniq);
    expect(r.errors).toHaveLength(0);
    expect(r.warnings.some((w) => w.includes('查重提示'))).toBe(true);
    expect(runSubmit({ customer_id: '', opp_name: 'o1' }, uniq).warnings).toHaveLength(0);
  });
  it('unique：数组行组合重复 → WARNING；无重复 → 静默', () => {
    const rows: SubmitRule[] = [{ ...uniq[0], fields: ['lines.material_id', 'lines.price'] }];
    const dup = { lines: [{ material_id: 'm1', price: 10 }, { material_id: 'm1', price: 10 }] };
    expect(runSubmit(dup, rows).warnings.some((w) => w.includes('查重提示'))).toBe(true);
    const ok = { lines: [{ material_id: 'm1', price: 10 }, { material_id: 'm2', price: 10 }] };
    expect(runSubmit(ok, rows).warnings).toHaveLength(0);
  });
});

describe('runSubmit fail-closed（配置错误不放行）', () => {
  it('未知函数名 → ERROR 阻断', () => {
    const bad = [{ name: '坏规则', function: 'avg', fields: ['amount'], operator: 'eq', compare: { valueType: 'fixed', value: 1 }, verifyType: 'ERROR', message: 'x' }] as unknown as SubmitRule[];
    const r = runSubmit(baseDoc(), bad, []);
    expect(r.errors.some((e) => e.includes('配置错误'))).toBe(true);
  });
  it('sum 缺 compare → ERROR 阻断', () => {
    const bad: SubmitRule[] = [{ name: '坏合计', function: 'sum', fields: ['items.amount'], operator: 'eq', verifyType: 'ERROR', message: 'x' }];
    const r = runSubmit(baseDoc(), bad, []);
    expect(r.errors.some((e) => e.includes('缺 compare'))).toBe(true);
  });
  it('count 计非数值行（非空即计数）', () => {
    const c: SubmitRule[] = [{ name: '行数', function: 'count', fields: ['lines.code'], operator: 'gte', compare: { valueType: 'fixed', value: 2 }, verifyType: 'ERROR', message: '至少两行' }];
    expect(runSubmit({ lines: [{ code: 'a' }, { code: 'b' }] }, c).errors).toHaveLength(0);
    expect(runSubmit({ lines: [{ code: 'a' }] }, c).errors.some((e) => e.includes('行数'))).toBe(true);
  });
});

describe('linkage tier/noticeKey + 条件深度上限', () => {
  it('then.tier 落到字段态（渲染器按 ctx.tier 过滤）', () => {
    const rules: LinkageRule[] = [{
      name: '大额走 pro 档', when: { fieldCode: 'amount', operator: 'gt', value: 5000 },
      then: [{ fieldCode: 'approver_id', tier: ['pro'] }],
    }];
    const st = linkageState(baseDoc({ amount: 6000 }), rules);
    expect(st.approver_id.tier).toEqual(['pro']);
  });
  it('noticeKey 直通（渲染器翻译）', () => {
    const rules: LinkageRule[] = [{
      name: '提示', when: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' },
      then: [{ fieldCode: 'amount', notice: '核对', noticeKey: 'expense.amount_check' }],
    }];
    const st = linkageState(baseDoc(), rules);
    expect(st.amount.noticeKey).toBe('expense.amount_check');
  });
  it('嵌套超 3 层 → fail-closed 不命中', () => {
    const deep: UiCondition = { all: [{ all: [{ all: [{ all: [{ fieldCode: 'status', operator: 'eq', value: 'DRAFT' }] }] }] }] };
    expect(evalCond(deep, baseDoc())).toBe(false);
    const ok3: UiCondition = { all: [{ all: [{ all: [{ fieldCode: 'status', operator: 'eq', value: 'DRAFT' }] }] }] };
    expect(evalCond(ok3, baseDoc())).toBe(true);
  });
});

describe('checkSchemaVersion 版本协商', () => {
  it('v1.0 实例（无 minor）→ ok', () => {
    expect(checkSchemaVersion({ schemaVersion: 1, entityCode: 't', views: {} })).toBe('ok');
  });
  it('未知 minor → warn（渲染继续+黄条）', () => {
    expect(checkSchemaVersion({ schemaVersion: 1, minor: 2, entityCode: 't', views: {} })).toBe('warn-unknown-minor');
  });
  it('未知大版本 → error（拒绝渲染）', () => {
    expect(checkSchemaVersion({ schemaVersion: 2, entityCode: 't', views: {} })).toBe('error-bad-major');
  });
  it('历史别名 schemaMinor 照读', () => {
    expect(checkSchemaVersion({ schemaVersion: 1, schemaMinor: 1, entityCode: 't', views: {} })).toBe('ok');
  });
});

describe('fieldVisible 档位门控（预览/运行时同源；曾踩坑：RulesPanel 可配 tier 但渲染器不消费）', () => {
  it('未声明 tier ⇒ 不限制，visible=true 即可见', () => {
    expect(fieldVisible({ visible: true, required: false, readonly: false, disabled: false, notice: '', noticeKey: '' })).toBe(true);
    expect(fieldVisible(undefined)).toBe(true);
  });

  it('声明 tier 且 ctx.tier 命中 ⇒ 可见', () => {
    const st = { visible: true, required: false, readonly: false, disabled: false, notice: '', noticeKey: '', tier: ['pro', 'max'] };
    expect(fieldVisible(st, { tier: 'pro' })).toBe(true);
  });

  it('声明 tier 但 ctx.tier 未命中/缺失 ⇒ 隐藏（负向：订阅档内容对低档用户不可见）', () => {
    const st = { visible: true, required: false, readonly: false, disabled: false, notice: '', noticeKey: '', tier: ['pro'] };
    expect(fieldVisible(st, { tier: 'free' })).toBe(false);
    expect(fieldVisible(st, {})).toBe(false);
  });

  it('联动 then.tier 落进 state 后由 fieldVisible 裁决（端到端）', () => {
    const rules: LinkageRule[] = [{
      name: 'pro 档看预估赔付', when: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' },
      then: [{ fieldCode: 'amount', tier: ['pro'] }],
    }];
    const st = linkageState(baseDoc(), rules);
    expect(fieldVisible(st.amount, { tier: 'pro' })).toBe(true);
    expect(fieldVisible(st.amount, { tier: 'free' })).toBe(false);
  });

  it('负向探针：visible=false 优先于 tier 命中（静态隐藏不可被档位解锁）', () => {
    const st = { visible: false, required: false, readonly: false, disabled: false, notice: '', noticeKey: '', tier: ['pro'] };
    expect(fieldVisible(st, { tier: 'pro' })).toBe(false);
  });
});

/* ==================================================================
 * N4：控件级校验 validateControlValues（对标宜搭/PowerApps 字段级约束）
 *
 * 判别力自证（每个 it 都问「删掉实现哪一行会红」）：
 * - 必填覆盖：删 `c.required === true && !baseRequired[code]` 的 baseRequired 守卫 → 双报用例红
 * - min/max：删任一 if → 对应越界用例红
 * - maxLength/pattern：删任一 if → 对应用例红
 * - 非法正则 fail-closed：删 catch 分支 → 该用例红（错误信息从「配置错误」变「静默放行」）
 * ================================================================== */
describe('validateControlValues 控件级校验（N4）', () => {
  it('控件必填覆盖：元数据非必填 + control.required=true + 空值 → ERROR', () => {
    const ctrls: Control[] = [{ fieldCode: 'tax_no', controlKind: 'INPUT', required: true }];
    const r = validateControlValues({ tax_no: '' }, ctrls, {});
    expect(r.errors.some((e) => e.includes('tax_no') && e.includes('不能为空'))).toBe(true);
  });

  it('必填覆盖不与 runSubmit 双报：元数据已 required 时本函数让位（baseRequired 命中）', () => {
    // 关键语义：同一字段元数据已必填 ⇒ 必填由 runSubmit 报；本函数不得再报一次
    const ctrls: Control[] = [{ fieldCode: 'tax_no', controlKind: 'INPUT', required: true }];
    const r = validateControlValues({ tax_no: '' }, ctrls, { tax_no: true });
    expect(r.errors.some((e) => e.includes('不能为空'))).toBe(false);
  });

  it('必填覆盖：有值时通过', () => {
    const ctrls: Control[] = [{ fieldCode: 'tax_no', controlKind: 'INPUT', required: true }];
    expect(validateControlValues({ tax_no: '91310000X' }, ctrls, {}).errors).toHaveLength(0);
  });

  it('未声明 required 且空值 → 不报（不制造噪音）', () => {
    const ctrls: Control[] = [{ fieldCode: 'remark', controlKind: 'INPUT' }];
    expect(validateControlValues({ remark: '' }, ctrls, {}).errors).toHaveLength(0);
  });

  it('数值型 min 越界 → ERROR；界内 → 通过（MONEY/NUMBER/SLIDER 同口径）', () => {
    const ctrls: Control[] = [{ fieldCode: 'amount', controlKind: 'MONEY', props: { min: 0, max: 5000 } }];
    expect(validateControlValues({ amount: -1 }, ctrls, {}).errors.some((e) => e.includes('不能小于'))).toBe(true);
    expect(validateControlValues({ amount: 5001 }, ctrls, {}).errors.some((e) => e.includes('不能大于'))).toBe(true);
    expect(validateControlValues({ amount: 100 }, ctrls, {}).errors).toHaveLength(0);
    // 边界值（=min / =max）算通过
    expect(validateControlValues({ amount: 0 }, ctrls, {}).errors).toHaveLength(0);
    expect(validateControlValues({ amount: 5000 }, ctrls, {}).errors).toHaveLength(0);
  });

  it('非数值型不因 min/max 报范围错（min/max 只对数值型生效）', () => {
    const ctrls: Control[] = [{ fieldCode: 'remark', controlKind: 'INPUT', props: { min: 0, max: 5 } }];
    expect(validateControlValues({ remark: '很长的一段文字' }, ctrls, {}).errors).toHaveLength(0);
  });

  it('文本型 maxLength 超长 → ERROR；未超长 → 通过', () => {
    const ctrls: Control[] = [{ fieldCode: 'title', controlKind: 'INPUT', props: { maxLength: 5 } }];
    expect(validateControlValues({ title: '一二三四五六' }, ctrls, {}).errors.some((e) => e.includes('长度'))).toBe(true);
    expect(validateControlValues({ title: '一二三四五' }, ctrls, {}).errors).toHaveLength(0);
  });

  it('文本型 pattern 不匹配 → ERROR；匹配 → 通过', () => {
    const ctrls: Control[] = [{ fieldCode: 'invoice_no', controlKind: 'INPUT', props: { pattern: '^[A-Z]{2}\\d+$' } }];
    expect(validateControlValues({ invoice_no: 'ab123' }, ctrls, {}).errors.some((e) => e.includes('不符合格式'))).toBe(true);
    expect(validateControlValues({ invoice_no: 'AB123' }, ctrls, {}).errors).toHaveLength(0);
  });

  it('非法正则 → fail-closed 报配置错误（不静默放行，与 runSubmit 同口径）', () => {
    const ctrls: Control[] = [{ fieldCode: 'invoice_no', controlKind: 'INPUT', props: { pattern: '([' } }];
    const r = validateControlValues({ invoice_no: 'AB123' }, ctrls, {});
    expect(r.errors.some((e) => e.includes('配置错误') && e.includes('pattern'))).toBe(true);
  });

  it('虚拟控件跳过（STAT/CHART 等非字段控件不做值校验）', () => {
    const ctrls: Control[] = [{ fieldCode: 'total_stat', controlKind: 'STAT', virtual: true, required: true, props: { maxLength: 1 } }];
    expect(validateControlValues({ total_stat: '任意长文本' }, ctrls, {}).errors).toHaveLength(0);
  });

  it('空值不触发最值/长度/正则（非必填空值直接跳过，避免「空值报格式错」）', () => {
    const ctrls: Control[] = [{ fieldCode: 'invoice_no', controlKind: 'INPUT', props: { pattern: '^[A-Z]+$' } }];
    expect(validateControlValues({ invoice_no: '' }, ctrls, {}).errors).toHaveLength(0);
  });

  it('与 runSubmit 叠加互不覆盖（控件级越界 + 提交规则错 同时可见）', () => {
    // amount=500：控件级 max:100 越界（报「不能大于」）；提交规则 min>=600 也越界（报「不得低于」）
    const ctrls: Control[] = [{ fieldCode: 'amount', controlKind: 'MONEY', props: { max: 100 } }];
    const rules: SubmitRule[] = [{
      name: '下限', function: 'min', fields: ['amount'], operator: 'gte',
      compare: { valueType: 'fixed', value: 600 }, verifyType: 'ERROR', message: '不得低于600',
    }];
    const cv = validateControlValues({ amount: 500 }, ctrls, {});
    const sub = runSubmit({ amount: 500 }, rules);
    expect(cv.errors.some((e) => e.includes('不能大于'))).toBe(true);
    expect(sub.errors.some((e) => e.includes('不得低于600'))).toBe(true);
  });
});


/* ============================================================================
 * N7：字段级角色/档位可见性（control.props.roles / props.tier）
 *
 * 取证起点：这两个键协议与 PROP_WHITELIST 早已收录，但引擎零求值 ⇒「配了不生效」。
 * 本组用例锁死接通后的行为契约，并覆盖**权限类判定的 fail-closed 取向**。
 * ========================================================================== */
describe('N7 字段级角色可见性（props.roles）', () => {
  const sections = (roles?: string[]): Section[] => [
    { id: 's1', title: '基本信息', controls: [{ fieldCode: 'amount', controlKind: 'MONEY', props: roles ? { roles } : undefined }] },
  ];

  it('未声明 roles ⇒ 所有人可见（不限制）', () => {
    const st = controlBaseStates(sections());
    expect(st.amount).toBeUndefined();
    expect(fieldVisible(linkageState({}, [], {}, {}, st)['amount'], { roles: ['anyone'] })).toBe(true);
  });

  it('声明 roles 且 ctx.roles 命中 ⇒ 可见', () => {
    const st = controlBaseStates(sections(['legal', 'admin']));
    const s = linkageState({}, [], {}, {}, st)['amount'];
    expect(s.roleRequired).toEqual(['legal', 'admin']);
    expect(fieldVisible(s, { roles: ['legal'] })).toBe(true);
  });

  it('声明 roles 但 ctx.roles 未命中 ⇒ 隐藏', () => {
    const st = controlBaseStates(sections(['legal']));
    expect(fieldVisible(linkageState({}, [], {}, {}, st)['amount'], { roles: ['sales'] })).toBe(false);
  });

  it('多角色是「或」语义（命中任一即可见）', () => {
    const st = controlBaseStates(sections(['legal', 'admin']));
    const s = linkageState({}, [], {}, {}, st)['amount'];
    expect(fieldVisible(s, { roles: ['admin', 'sales'] })).toBe(true);
  });

  it('ctx.roles 缺失 ⇒ 隐藏（fail-closed：权限判定默认不放行）', () => {
    const st = controlBaseStates(sections(['legal']));
    expect(fieldVisible(linkageState({}, [], {}, {}, st)['amount'], {})).toBe(false);
    expect(fieldVisible(linkageState({}, [], {}, {}, st)['amount'], { roles: [] })).toBe(false);
  });

  it('空数组 roles ≡ 未声明（不限制），与 resolveVariant 口径一致', () => {
    expect(rolesVisible([], { roles: ['x'] })).toBe(true);
    expect(rolesVisible(undefined, {})).toBe(true);
    // 与变体判定的同口径校验：空集合**不构成限制**——matchVariant 的判据是
    // `if (m.roles && m.roles.length)`，空数组为 falsy ⇒ 该维度不参与匹配 ⇒ 变体命中。
    // 这与 rolesVisible([]) = true（不限制）同向；两个判定都遵循「空 = 不限制」。
    //
    // 注意原断言期望回落 base（认为空数组不应命中任何变体），与引擎实际相反 ——
    // 且与 resolveVariant 既有的空 match 兜底语义冲突。若改成「空数组不命中」，
    // 则 `match: { roles: [] }` 的变体永远不可达，而「缺省 match」又能命中，
    // 两者语义割裂。故按引擎既有语义锁定：空集合 = 不限制 = 该维度通配。
    const s: BoneUiSchema = {
      schemaVersion: '1.0', entityCode: 'e', displayModes: ['create'],
      views: { form: { sections: [{ id: 'base', title: '基本信息', controls: [] }] } },
      variants: [{ key: 'v', label: 'v', priority: 1, match: { roles: [] }, views: { form: { sections: [{ id: 'vsec', title: '变体区', controls: [] }] } } }],
    } as unknown as BoneUiSchema;
    // 断言性质「空 roles 不构成限制 ⇒ 命中该变体」而非引用同一性
    expect(resolveVariant(s, { roles: ['nobody'] }).views.form?.sections?.[0]?.id).toBe('vsec');
  });

  it('联动不得放宽角色门禁（then.visible=true也 不能让越权角色看见）', () => {
    // 这是本组最关键的不变量：角色是安全约束，联动规则只负责业务显隐，
    // 若可被 then.visible 覆盖 ⇒ 等于给了一个越权后门。
    const st = controlBaseStates(sections(['legal']));
    const rules: LinkageRule[] = [{ name: '强行显示', when: { fieldCode: 'x' }, then: [{ fieldCode: 'amount', visible: true }] }];
    const s = linkageState({}, rules, { roles: ['sales'] }, {}, st)['amount'];
    expect(s.visible).toBe(true);          // 联动确实把 visible 置回了 true
    expect(fieldVisible(s, { roles: ['sales'] })).toBe(false); // 但角色门禁仍拦住
  });

  it('与联动 tier 可叠加（角色命中 + 档位命中 才可见）', () => {
    const secs: Section[] = [{ id: 's1', title: '', controls: [
      { fieldCode: 'vip_note', props: { roles: ['legal'], tier: ['pro'] } },
    ] }];
    const s = linkageState({}, [], {}, {}, controlBaseStates(secs))['vip_note'];
    expect(fieldVisible(s, { roles: ['legal'], tier: 'free' })).toBe(false);
    expect(fieldVisible(s, { roles: ['legal'], tier: 'pro' })).toBe(true);
    expect(fieldVisible(s, { roles: ['sales'], tier: 'pro' })).toBe(false);
  });

  it('同fieldCode 多控件：roleRequired 取交集（更严者胜）', () => {
    const secs: Section[] = [{ id: 's1', title: '', controls: [
      { fieldCode: 'x', props: { roles: ['legal', 'admin'] } },
      { fieldCode: 'x', props: { roles: ['admin', 'it'] } },
    ] }];
    expect(controlBaseStates(secs).x.roleRequired).toEqual(['admin']);
  });

  it('visible:false 与 roles 叠加时两者都不可绕过', () => {
    const secs: Section[] = [{ id: 's1', title: '', controls: [
      { fieldCode: 'x', visible: false, props: { roles: ['legal'] } },
    ] }];
    const s = linkageState({}, [], { roles: ['legal'] }, {}, controlBaseStates(secs))['x'];
    expect(fieldVisible(s, { roles: ['legal'] })).toBe(false);
  });

  it('baseStates 只保留有内容的键（避免上百默认控件污染联动热路径）', () => {
    const many: Section[] = [{ id: 's1', title: '', controls: Array.from({ length: 50 }, (_, i) => ({ fieldCode: `f${i}` })) }];
    expect(Object.keys(controlBaseStates(many))).toHaveLength(0);
  });

  it('显式 visible:true 不产生约束条目（写了 true 与不写等价，不得进baseStates）', () => {
    // 回归护栏：`visible: true` 是**无约束力**的显式声明。若基线态把它收成
    // {visible:true}，这些控件会白白进入联动每轮的重算与 merge（100 个控件 = 100 条），
    // 正好违背 controlBaseStates 的初衷。
    // 变异验证：把实现改回 `if (c.visible !== undefined) next.visible = c.visible !== false`
    // 会让本例变红——此断言是专门为守住该性质而存在。
    const secs: Section[] = [
      { id: 's1', title: '', controls: [{ fieldCode: 'a', visible: true }, { fieldCode: 'b' }] },
    ];
    expect(controlBaseStates(secs)).toEqual({});
    // 行为等价性：显式 true 与不写，可见性判定结果必须一致（都是可见）
    const stA = controlBaseStates([{ id: 's', title: '', controls: [{ fieldCode: 'x', visible: true }] }]);
    expect(fieldVisible(linkageState({}, [], {}, {}, stA)['x'], {})).toBe(true);
  });

  it('含空串/空格的 roles 被清洗掉 ⇒ 退化为不限制（脏数据不导致全量隐藏）', () => {
    // 协议要求非空 string[]；DB 里若存了脏形态，加载时不得把控件整体藏掉。
    // 清洗后为空集合 ⇒ 与"未声明"同义（不限制），这是 fail-open 的**数据健壮性**取向，
    // 与"已声明合法角色但 ctx 无角色"时的 fail-closed 是两件不同的事，勿混淆。
    const bad = controlBaseStates([{ id: 's', title: '', controls: [
      { fieldCode: 'x', props: { roles: ['', '  '] as unknown as string[] } },
    ] }]);
    expect(bad.x).toBeUndefined();
    expect(rolesVisible(bad.x?.roleRequired, { roles: ['x'] })).toBe(true);
  });

  it('部分有效角色时保留有效项（脏数据只剔除无效项，不整条丢弃）', () => {
    const mixed = controlBaseStates([{ id: 's', title: '', controls: [
      { fieldCode: 'x', props: { roles: ['legal', ''] as unknown as string[] } },
    ] }]);
    expect(mixed.x?.roleRequired).toEqual(['legal']);
    expect(rolesVisible(mixed.x?.roleRequired, { roles: ['sales'] })).toBe(false);
    expect(rolesVisible(mixed.x?.roleRequired, { roles: ['legal'] })).toBe(true);
  });

  it('角色名带空格时归一化 trim（否则配了等于没配）', () => {
    // Select mode="tags" 很容易留下 ' legal ' 这种形态；不归一化则永远匹配不上。
    const spaced = controlBaseStates([{ id: 's', title: '', controls: [
      { fieldCode: 'x', props: { roles: [' legal '] } },
    ] }]);
    expect(spaced.x?.roleRequired).toEqual(['legal']);
    expect(rolesVisible(spaced.x?.roleRequired, { roles: ['legal'] })).toBe(true);
  });
});

describe('N7 mask 的定性：仅建模标注，不构成安全控制', () => {
  const lintOf = (props: Record<string, unknown>, code = 'id_card'): LintIssue[] =>
    lintSchema({
      schemaVersion: '1.3', entityCode: 'e', displayModes: ['create'],
      views: { form: { sections: [{ id: 's', title: '', controls: [{ fieldCode: code, props }] }] } },
    } as unknown as BoneUiSchema, new Set([code]), {});

  it('props.mask 存在时 lint 必须报 warning 提醒"运行时未脱敏"', () => {
    const warn = lintOf({ mask: 'last4' }).filter((i) => i.level === 'warning' && i.message.includes('mask'));
    expect(warn.length).toBeGreaterThan(0);
    expect(warn[0].message).toMatch(/仍返回明文|未脱敏/);
    expect(warn[0].message).toMatch(/不可当作安全控制|切勿当作安全控制/);
  });

  it('mask 非法值报 error（hash/last4 之外直接拒绝）', () => {
    expect(lintOf({ mask: 'xxx' }).some((i) => i.level === 'error' && i.message.includes('mask'))).toBe(true);
  });

  it('mask 合法值不报 error（只报那条"未脱敏"warning）', () => {
    const errs = lintOf({ mask: 'hash' }).filter((i) => i.level === 'error' && i.message.includes('mask'));
    expect(errs).toHaveLength(0);
  });

  it('roles 传错类型报 error（避免"配了全隐藏"无从排查）', () => {
    expect(lintOf({ roles: 'legal' }, 'a').some((i) => i.level === 'error' && i.message.includes('roles'))).toBe(true);
  });

  it('roles 空数组报 warning 引导删键（语义等价不限制，留着只让人误以为配过）', () => {
    expect(lintOf({ roles: [] }).some((i) => i.level === 'warning' && i.message.includes('roles'))).toBe(true);
  });

  it('合法 roles 不报任何 roles 相关 error', () => {
    const issues = lintOf({ roles: ['legal'] });
    expect(issues.some((i) => i.message.includes('roles'))).toBe(false);
  });
});
