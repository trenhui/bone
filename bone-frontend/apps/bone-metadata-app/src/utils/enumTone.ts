import type { EnumOption } from '@bone/shared-types';

/**
 * ENUM 徽标的**语义色**——状态类枚举按含义取色，而不是按选项下标轮转。
 *
 * <p><b>为什么不能用下标轮转</b>：`ENUM_TAG_COLORS[idx % 10]` 让颜色取决于「选项在
 * 数组里排第几」。同一个 `APPROVED`，在 A 实体是绿色、在 B 实体是橙色——用户被迫记
 * 「这个列表里哪个颜色代表通过」，跨列表的肌肉记忆完全失效；新增一个选项还会让**后面
 * 所有选项整体换色**（一次建模改动引发全站视觉漂移）。
 *
 * <p>业界一致做法是状态色固定语义（antd Badge / Status 预设色、Salesforce Lightning
 * 的 `slds-badge` 状态族、ServiceNow 状态色板）：草稿=中性、流转中=进行态、通过=成功、
 * 驳回=失败、待处理=警告。本模块把这条约定固化成纯函数，供列表与详情共用同一判据。
 *
 * <p><b>为什么同时认 code 与中文 label</b>：平台里有两种历史数据——英文码（`APPROVED`）
 * 与已中文化的 label（`已审批`）。只认 code 会让中文 label 的枚举退回轮转取色；只认
 * label 又会漏掉尚未中文化的枚举。故 code 优先（更精确，不受译文调整影响），label 兜底。
 *
 * <p><b>为什么不返回颜色字面量</b>：返回 antd 的**状态色语义名**（`success`/`error`…），
 * 由组件库映射到主题色，暗色主题与品牌换肤自动跟随。
 */
export type EnumTone = 'default' | 'processing' | 'success' | 'error' | 'warning';

/**
 * 英文码 → 语义色。只收「含义确定」的词：拿不准的一律不映射（回落轮转），
 * 宁可没有颜色，也不要给出错误暗示（红/绿是有承诺的）。
 */
const CODE_TONES: Record<string, EnumTone> = {
  // 中性：还没进入流程
  DRAFT: 'default',
  NEW: 'default',
  INIT: 'default',
  INITIAL: 'default',
  TODO: 'default',

  // 进行态：已提交、在审、处理中
  SUBMITTED: 'processing',
  SUBMITTING: 'processing',
  IN_PROGRESS: 'processing',
  PROCESSING: 'processing',
  REVIEWING: 'processing',
  APPROVING: 'processing',
  RUNNING: 'processing',
  SYNCING: 'processing',

  // 成功终态
  APPROVED: 'success',
  PASS: 'success',
  PASSED: 'success',
  COMPLETED: 'success',
  FINISHED: 'success',
  DONE: 'success',
  CLOSED: 'success',
  PAID: 'success',
  SETTLED: 'success',
  ACTIVE: 'success',
  ENABLED: 'success',
  VALID: 'success',
  NORMAL: 'success',
  RECEIVED: 'success',
  DELIVERED: 'success',
  // 在途：货已出但流程未完，不能给「成功」的承诺
  SHIPPED: 'processing',
  ORDERED: 'processing',

  // 失败终态
  REJECTED: 'error',
  FAILED: 'error',
  FAILURE: 'error',
  CANCELLED: 'error',
  CANCELED: 'error',
  ABANDONED: 'error',
  TERMINATED: 'error',
  EXPIRED: 'error',
  INVALID: 'error',
  ABNORMAL: 'error',

  // 中间态：需要人介入
  PENDING: 'warning',
  WAITING: 'warning',
  ON_HOLD: 'warning',
  SUSPENDED: 'warning',
  PAUSED: 'warning',
  OVERDUE: 'warning',
  PROBATION: 'warning',
  WARNING: 'warning',

  // 已建模但标记废弃
  DEPRECATED: 'default',
  DISABLED: 'default',
  INACTIVE: 'default',
};

/**
 * 中文 label → 语义色（兜底）。**顺序敏感**：否定式必须先于肯定式
 * （「未通过」含「通过」、「已取消」含「取消」与「已」）。
 */
const LABEL_TONES: ReadonlyArray<readonly [RegExp, EnumTone]> = [
  // 否定/失败优先
  [/未通过|不通过|未批准|已否决/, 'error'],
  [/驳回|拒绝|否决|失败|取消|作废|撤销|撤回|过期|超期|失效|异常|停用|禁用|终止|冻结/, 'error'],
  // 进行态（含在途：已发货/已下单都不是终态）
  [/审批中|审核中|复核中|处理中|进行中|已提交|提交中|待审|评审中|流转中|运输中|派发|已发货|已下单|待补货/, 'processing'],
  // 成功终态
  [/通过|批准|完成|已支付|已打款|打款|已结算|生效|有效|正常|启用|成功|已闭环|已收货|已送达/, 'success'],
  // 中间态需要人介入
  [/待补|待处理|挂起|暂停|中止|逾期|试用|观察|警告|待办|排队/, 'warning'],
  // 中性
  [/草稿|新建|初始|未开始|未启用/, 'default'],
];

/** 归一化 code：去空白、转大写（`draft` 与 `Draft` 视为同一状态） */
function normalizeCode(code: string): string {
  return code.trim().toUpperCase();
}

/**
 * 取枚举选项的语义色；无法判定时返回 `null`（调用方回落轮转色板，保持原有观感）。
 */
export function enumToneOf(
  option: Pick<EnumOption, 'code' | 'label' | 'state'> | null | undefined,
): EnumTone | null {
  if (!option) return null;
  // 三态生命周期标记优先：已废弃的选项不论词义都不该再吸引注意力
  if (option.state === 'DEPRECATED' || option.state === 'DISABLED') return 'default';

  const byCode = CODE_TONES[normalizeCode(option.code || '')];
  if (byCode) return byCode;

  const label = (option.label || '').trim();
  if (!label || label === option.code) return null;
  for (const [pattern, tone] of LABEL_TONES) {
    if (pattern.test(label)) return tone;
  }
  return null;
}
