/**
 * 预览沙盒场景数据。
 *
 * 为什么需要它：界面配置的正确性无法靠"看 JSON"判断 —— 只有把真实数据灌进去，
 * 才能看见联动是否触发、校验是否拦截。这是配置器区别于"JSON 编辑器"的核心价值。
 *
 * 场景按键名匹配实体字段（只填得进去的才填），因此同一份场景可以套到不同实体上，
 * 不绑定具体 entityCode。
 */

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
