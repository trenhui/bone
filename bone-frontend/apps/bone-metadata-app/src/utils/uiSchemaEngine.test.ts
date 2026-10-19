import { describe, expect, it } from 'vitest';
import {
  checkSchemaVersion,
  evalCond,
  fieldVisible,
  linkageState,
  pathVal,
  resolveVariant,
  resolveVisibleActions,
  runSubmit,
  validateControlValues,
  type ActionBarItem,
  type BoneUiSchema,
  type UiCondition,
  type Control,
  type LinkageRule,
  type SubmitRule,
} from './uiSchemaEngine';

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
    entityCode: 't_expense_claim',
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

