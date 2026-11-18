/**
 * 履约协同页面纯逻辑：状态投影、错误降级、按钮可见性。
 * 只做只读投影，所有业务前置条件（PO 是否已确认、余额、TDS、Finance 裁决）由后端裁决，
 * 前端不内置规则、不本地推进状态、不把 997 语法回执当成业务匹配通过。
 */

export type AccessLevel = 'readwrite' | 'readonly' | 'unavailable';

/** 履约错误码 → 可读提示（31501–31506）。未知码原样返回，不臆造中文。 */
const FULFILLMENT_ERRORS: Record<string, string> = {
  '31501': '发货通知与收货数量/批次不符，请核对后重发',
  '31502': '发票号已存在，重复提交被拒绝',
  '31503': '采购订单未确认，不能发货或开票',
  '31504': '开票金额超出未开票余额，请调整金额后重试',
  '31505': '采购域无发票放行权限，放行只能由财务裁决',
  '31506': '997 为语法回执，不代表业务匹配通过',
  '409': '当前状态不允许该操作',
  '403': '无权限执行该操作（或跨供应商越权被拒）',
  '404': '能力未启用或数据不存在',
};

export function degradeMessage(code: string, raw?: string): string {
  return FULFILLMENT_ERRORS[code] ?? (raw && raw.trim() ? raw : code);
}

/** 从 axios/信封错误中提取稳定错误码，优先 errorCode（履约 315xx 在 errorCode 字段）。 */
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

/**
 * 发票状态投影：997 语法回执只推进到 MATCH_PENDING，绝不能显示“已匹配/MATCHED”。
 * 只有后端明确的 MATCHED 才显示匹配通过，且 Finance 事件才可能写入该状态。
 */
export function invoiceStateProjection(status?: string): string {
  if (!status || !status.trim()) return '无法确认';
  if (status === 'MATCH_PENDING') return '等待财务匹配（语法回执不代表通过）';
  if (status === 'MATCHED') return '已匹配（财务裁决）';
  return status;
}

/** PO 仅在 SENT 态允许前端展示“确认”入口；其它态（含履约保留态）无入口。 */
export function canAck(poStatus?: string): boolean {
  return poStatus === 'SENT';
}

/** 只有 PO 已确认后，才展示 ASN 登记入口。 */
export function canCreateAsn(acknowledged?: boolean): boolean {
  return acknowledged === true;
}

/** 例外只有 OPEN/SUPPLEMENTED 态允许补料；UNDER_REVIEW/RESOLVED/REJECTED 隐藏写按钮。 */
export function canSupplement(status?: string): boolean {
  return status === 'OPEN' || status === 'SUPPLEMENTED';
}

/** 只读/不可用态一律不展示写按钮。 */
export function canWrite(access: AccessLevel): boolean {
  return access === 'readwrite';
}
