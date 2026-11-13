/* AUTO-GENERATED from GET /api/v1/claim-config/dicts — do not edit by hand.
 * Regenerate: `npm run gen:dicts`. Single source of truth = backend ClaimConfigApplicationService.
 */

export const CLAIM_STATUS_DICT: Record<string, string> = {
  'DRAFT': '草稿',
  'ACCEPTED': '已签收',
  'PENDING_PRE_EXAM': '待初审分配',
  'PRE_EXAMINING': '初审中',
  'AUTO_PRE_EXAMINING': '自动化初审中',
  'PRE_EXAMINED': '已初审',
  'EVIDENCE_SUBMITTING': '补录中',
  'PENDING_ADJUST': '待核赔',
  'ADJUSTING': '核赔中',
  'ADJUSTED': '已审核',
  'REVIEWING': '复核中',
  'CLOSED_REJECTED': '已撤销/拒赔生效',
  'FINISHED': '已结案',
  'APPEALING': '申诉中'
};

export const AI_INVOLVEMENT_DICT: Record<string, string> = {
  'AI_ASSISTED': '含 AI 辅助生成内容',
  'AI_STUB': '未使用真实模型（确定性替身）',
  'AI_UNAVAILABLE': 'AI 能力未可用（规则降级）',
  'UNDETERMINED': '无法确认 AI 是否参与'
};

export const AI_RIGHTS_DICT: Record<string, string> = {
  'REQUEST_HUMAN_REVIEW': '要求人工复核',
  'APPEAL_WITHIN_48H': '48 小时内申诉',
  'EXPLANATION_OF_DECISION': '要求说明决定依据'
};

export const CONSUMPTION_REASON_DICT: Record<string, string> = {
  'APPLIED': '已自动放款',
  'AUTO_APPROVE_TOGGLE_OFF': '自动放款开关关闭',
  'NO_DECISION': '无决策记录',
  'CLAIM_NOT_FOUND': '案件不存在',
  'ALREADY_SETTLED': '案件已结案',
  'CAP_EXCEEDED': '超单笔上限',
  'DUAL_REVIEW_REQUIRED': '超双人复核门转双人',
  'RISK_GATE_BLOCKED': '风险闸拦截'
};

export const ZERO_REASON_DICT: Record<string, string> = {
  'ZERO_REASONABLE': '合理费用为 0（费用全部被判定为不合理/不可赔）',
  'ZERO_DEDUCT': '免赔未达（扣减免赔额后为 0）',
  'ZERO_PERCENT': '赔付比例为 0',
  'ZERO_LIMIT': '责任额度不足（本行额度已用尽）',
  'ZERO_LIMIT_EXHAUSTED': '责任额度已耗尽（历史占额已封顶）',
  'ZERO_NOT_COVERED': '不在保障范围'
};

export const REJECT_REASON_DICT: Record<string, string> = {
  '20': '20 目录外费用不赔付',
  '30': '30 责任范围除外',
  '40': '40 风险标记（转人工核实）',
  '03': '03 就诊医院不符合责任医院规则',
  '04': '04 诊断不符合责任免赔规则',
  '05': '05 合理费用 / 赔付比例为 0（不合理费用、非保障范围）',
  '06': '06 未达免赔额（免赔未消抵完）',
  '09': '09 无剩余保额（责任额度已用尽/封顶）'
};

export const AI_REJECT_REASON_DICT: Record<string, string> = {
  'EVIDENCE_INSUFFICIENT': '证据不足',
  'CLINICAL_UNREASONABLE': '临床不合理',
  'POLICY_EXCLUSION': '保单责任免除',
  'AMOUNT_MISMATCH': '金额不符',
  'FRAUD_SUSPECTED': '疑似欺诈',
  'OTHER': '其他（需人工补充）'
};

export const FRAUD_GATE_REASON_DICT: Record<string, string> = {
  'ANTI_FRAUD_CAPABILITY_MISSING': '增强反欺诈能力未集成（转人工）',
  'RISK_HIT': '命中风险',
  'VERIFY_NOT_PASSED': '验真未通过',
  'PASSED': '通过'
};

export const PERMISSION_DICT: Record<string, string> = {
  'claim:policy:read': '责任/保单配置读',
  'claim:policy:write': '责任/保单配置写',
  'claim:mapping:dispose': '映射处置',
  'claim:quota:read': '额度读',
  'claim:quota:write': '额度写',
  'claim:claims:read': '案件读',
  'claim:claims:write': '案件写',
  'claim:review:write': '复核写',
  'claim:decision:write': '核赔写',
  'claim:settlement:write': '结算写',
  'claim:appeal:write': '申诉写',
  'claim:inspection:write': '后督写'
};
