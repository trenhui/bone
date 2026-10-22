/**
 * 预览沙盒场景数据。
 *
 * 为什么需要它：界面配置的正确性无法靠"看 JSON"判断 —— 只有把真实数据灌进去，
 * 才能看见联动是否触发、校验是否拦截。这是配置器区别于"JSON 编辑器"的核心价值。
 *
 * 场景按键名匹配实体字段（只填得进去的才填），因此同一份场景可以套到不同实体上，
 * 不绑定具体 entityCode。
 *
 * 但「能套用」≠「该展示」：门诊快赔的演示数据套在报销单上毫无意义，只会造成领域混淆。
 * 因此消费方（预览沙盒 Tabs、「载入场景样例」下拉）必须先做领域相关性过滤 ——
 * 以场景/样例引用的字段与实体字段的重合率为判据，不绑定 entityCode（用户可自取 code，不可靠）。
 */

import type { BoneUiSchema } from '@bone/ui-schema';

export interface Scenario {
  key: string;
  label: string;
  desc: string;
  data: Record<string, unknown>;
}

/** 空白场景：按字段类型生成初值，用于从零验证 */
export const SCENARIO_BLANK: Scenario = {
  key: 'blank',
  label: '空白',
  desc: '所有字段取类型默认值，用于从零验证布局与必填',
  data: {},
};

/** 场景一：差旅报销（专票税号联动 + 明细合计一致） */
export const SCENARIO_EXPENSE: Scenario = {
  key: 'expense',
  label: '差旅报销',
  desc: '发票类型=专票 → 税号显示且必填；明细合计 128.5 = 申报金额，应通过校验',
  data: {
    title: '深圳客户现场支持差旅费',
    category_id: 'CAT-TRAVEL',
    applicant: '张三',
    expense_date: '2026-10-08',
    amount: 128.5,
    currency: 'CNY',
    expense_start: '2026-10-06',
    expense_end: '2026-10-08',
    invoice_type: 'SPECIAL',
    tax_no: '', // 故意留空：用于验证「专票必填税号」是否真能拦住
    items: [
      { name: '高铁票', amount: 88.5, category: '交通' },
      { name: '住宿' , amount: 40, category: '住宿' },
    ],
    attachment: [],
    approver_id: 'U-001',
    status: 'DRAFT',
  },
};

/** 场景二：智能理赔门诊快赔（快赔资格四条件 + 发票一致 + 拒赔要素） */
export const SCENARIO_CLAIM: Scenario = {
  key: 'claim',
  label: '门诊快赔',
  desc: '四条件全满足 → 亮出快赔入口；改 status=REJECTING 可验证拒赔要素完整性拦截',
  data: {
    policy_no: 'PLC-2026-000123',
    insured_id_hash: 'sha256:9f2c…a71b',
    insured_name: '李四',
    claim_type: 'OUTPATIENT',
    hospital_level: 'L2',
    invoice_no: 'INV-2026-8891',
    invoice_date: '2026-10-10',
    claim_amount: 480,
    calc_amount: 480,
    invoice_amount: 128.5,
    items: [
      { name: '挂号费', amount: 28.5, category: '门诊' },
      { name: '西药费', amount: 100, category: '药品' },
    ],
    evidences: ['门诊病历.pdf', '发票影像.jpg'],
    deductible_used: 0,
    payable_amount: 480,
    fraud_flags: null, // 命中 R-FRAUD 标记会转人工，此处为空以走快赔
    coverage_clear: true,
    status: 'DRAFT',
    reject_reasons: [],
  },
};

export const SCENARIOS: Scenario[] = [SCENARIO_BLANK, SCENARIO_EXPENSE, SCENARIO_CLAIM];

/* ============================== 领域相关性过滤 ============================== */

/** 领域无关的通用键：多数实体都有，参与判定会虚增重合率，故排除 */
const GENERIC_KEYS = new Set(['title', 'status', 'items', 'attachment', 'approver_id', 'id']);

/**
 * 领域相关性判定：keys（场景/样例引用的字段）与实体字段的重合率 ≥ minRatio 才视为相关。
 * - 场景键里几乎全是领域特征字段（policy_no/claim_type vs invoice_type/tax_no），
 *   跨领域时重合率趋近 0，阈值 0.5 足以干净区分。
 * - 不绑定 entityCode：code 由用户自取，不可靠；字段签名才是实体的真实领域指纹。
 */
export function matchesEntityDomain(keys: string[], fieldCodes: string[], minRatio = 0.5): boolean {
  const domainKeys = keys.filter((k) => !GENERIC_KEYS.has(k));
  if (domainKeys.length === 0) return true;
  const set = new Set(fieldCodes);
  const hit = domainKeys.filter((k) => set.has(k)).length;
  return hit / domainKeys.length >= minRatio;
}

/** 场景是否适用于当前实体（预览沙盒 Tabs 按此过滤；空白场景无数据键，恒适用） */
export function scenarioApplies(sc: Scenario, fields: Array<{ code: string }>): boolean {
  return matchesEntityDomain(Object.keys(sc.data), fields.map((f) => f.code));
}

/** 从样例 schema 提取引用的字段码（表单控件 + 列表列），供样例适用性判定 */
function schemaFieldRefs(schema: BoneUiSchema): string[] {
  const out = new Set<string>();
  const views = schema.views as {
    form?: { sections?: Array<{ controls?: Array<{ fieldCode?: string }> }> };
    list?: { columns?: Array<{ fieldCode?: string }> };
  };
  views.form?.sections?.forEach((sec) => sec.controls?.forEach((c) => { if (c.fieldCode) out.add(c.fieldCode); }));
  views.list?.columns?.forEach((c) => { if (c.fieldCode) out.add(c.fieldCode); });
  return [...out];
}

/** 样例是否适用于当前实体（「载入场景样例」下拉按此过滤；样例定义在生成文件 samples.ts，判定逻辑放在本手工文件防再生成覆盖） */
export function sampleApplies(sample: { schema: BoneUiSchema }, fields: Array<{ code: string }>): boolean {
  return matchesEntityDomain(schemaFieldRefs(sample.schema), fields.map((f) => f.code));
}

/** 把场景数据套用到实体字段上（只填字段存在的键，其余忽略） */
export function applyScenario(
  scenario: Scenario,
  fieldCodes: string[],
  defaultOf: (code: string) => unknown,
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  fieldCodes.forEach((code) => {
    out[code] = code in scenario.data ? scenario.data[code] : defaultOf(code);
  });
  return out;
}
