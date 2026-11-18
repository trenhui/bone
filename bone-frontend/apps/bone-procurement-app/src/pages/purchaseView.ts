/**
 * 采购交易页面的纯可测逻辑。
 *
 * 只做只读投影：状态→允许事件、错误降级、错误对象归一、金额格式化。
 * 所有业务前置条件仍由后端守卫裁决，前端不内置规则、不本地推进状态。
 */

export type PurchaseEventOption = { event: string; label: string };

/** PO 状态 → 可显示事件（与后端 PoStatus 迁移表同语义）。履约段与终态无入口。 */
const PO_EVENTS: Record<string, PurchaseEventOption[]> = {
  DRAFT: [{ event: 'SUBMIT', label: '提交' }],
  SUBMITTED: [{ event: 'APPROVE', label: '审批通过' }],
  APPROVED: [{ event: 'SEND', label: '下达' }],
  // SENT 及履约保留状态、CANCELLED/CLOSED：无事件入口
};

/** PR 状态 → 可显示事件。 */
const PR_EVENTS: Record<string, PurchaseEventOption[]> = {
  DRAFT: [{ event: 'SUBMIT', label: '提交' }],
  SUBMITTED: [{ event: 'APPROVE', label: '审批通过' }],
  APPROVED: [{ event: 'CONVERT', label: '转采购订单' }],
};

export function allowedPoEvents(state?: string): PurchaseEventOption[] {
  return (state && PO_EVENTS[state]) || [];
}

export function allowedPrEvents(state?: string): PurchaseEventOption[] {
  return (state && PR_EVENTS[state]) || [];
}

/** 错误码 → 可读提示。未知码原样返回，不臆造中文。 */
const ERROR_MESSAGES: Record<string, string> = {
  '31003': '供应商交易资格未开通，无法建单',
  '31004': '法人实体不匹配，禁止跨法人下单',
  '31402': '订单总额与行金额合计不一致',
  '31405': '缺少价格快照输入，无法下达',
  '404': '能力未启用或数据不存在',
  '403': '无权限执行该操作',
};

export function degradeMessage(code: string, raw?: string): string {
  return ERROR_MESSAGES[code] ?? (raw && raw.trim() ? raw : code);
}

/** 从 axios/信封错误中提取稳定错误码。 */
export function fromApiError(err: unknown): string {
  if (!err || typeof err !== 'object') return 'UNKNOWN';
  const e = err as {
    response?: { status?: number; data?: { code?: number | string; errorCode?: string } };
    code?: number | string;
  };
  const errorCode = e.response?.data?.errorCode;
  if (errorCode) return String(errorCode);
  const code = e.response?.data?.code ?? e.response?.status ?? e.code;
  return code != null ? String(code) : 'UNKNOWN';
}

/** 未知状态码原样返回；空值显示「无法确认」。 */
export function safeStateProjection(state?: string): string {
  return state && state.trim() ? state : '无法确认';
}

/** 按币种四位小数格式化金额；无值返回「无法确认」。 */
export function formatMoney(amount?: string, currency?: string): string {
  if (amount == null || amount === '') return '无法确认';
  const n = Number(amount);
  if (!Number.isFinite(n)) return amount;
  return `${n.toFixed(4)} ${currency ?? ''}`.trim();
}
