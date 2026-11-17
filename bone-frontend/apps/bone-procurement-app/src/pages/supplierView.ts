/**
 * 供应商治理页面的纯可测逻辑。
 *
 * 从页面中抽出，原因有二：
 * 1. 状态→事件映射是「只读投影」：只决定给用户显示哪些入口，所有前置条件仍由后端守卫裁决；
 * 2. 便于在无 jsdom 环境对 PII 白名单、降级分类、分页归一做可证伪的断言。
 */
import { normalizeTotal } from '@bone/shared-utils';
import type { SupplierStateDTO } from '../types/supplier';
export type { SupplierStateDTO };

export interface TransitionEventOption {
  event: string;
  /** Vendor 迁移需来源声明；Supplier 迁移无此字段。 */
  source?: 'DOMAIN' | 'EXTERNAL_FINANCE';
  label: string;
}

/** Supplier 状态 → 可显示事件（与后端迁移表同语义，但不内置守卫裁决）。 */
const SUPPLIER_EVENTS: Record<string, TransitionEventOption[]> = {
  PROSPECT: [{ event: 'onboarding_submitted', label: '提交准入' }],
  ONBOARDING: [{ event: 'onboarding_approved', label: '准入批准' }],
  ACTIVE: [
    { event: 'suspended', label: '暂停合作' },
    { event: 'exit_approved', label: '申请退出' },
  ],
  SUSPENDED: [
    { event: 'restored', label: '恢复合作' },
    { event: 'corrective', label: '整改中' },
  ],
  CORRECTIVE: [
    { event: 'restored', label: '恢复合作' },
    { event: 'exit_approved', label: '申请退出' },
  ],
  // EXITED 是终态，无任何事件；未知状态也不臆造。
};

/** Vendor 状态 → 可显示事件。付款冻结的提起与解除都只接受 EXTERNAL_FINANCE（后端裁决，前端不提供域内解禁入口）。 */
const VENDOR_EVENTS: Record<string, TransitionEventOption[]> = {
  PENDING_VENDOR: [{ event: 'activation_passed', source: 'DOMAIN', label: '闸门开通' }],
  ACTIVE_VENDOR: [
    { event: 'payment_block_raised', source: 'EXTERNAL_FINANCE', label: '提起付款冻结' },
    { event: 'deactivate', source: 'DOMAIN', label: '停用' },
  ],
  PAYMENT_BLOCKED: [{ event: 'payment_block_released', source: 'EXTERNAL_FINANCE', label: '解除冻结（需财务复核）' }],
};

export function allowedSupplierEvents(state: string): TransitionEventOption[] {
  return SUPPLIER_EVENTS[state] ?? [];
}

export function allowedVendorEvents(state: string): TransitionEventOption[] {
  return VENDOR_EVENTS[state] ?? [];
}

/** 失败降级分类：404/未注册→能力未启用，403→无权限，其余显示后端 message 原文。 */
export function degradeMessage(code: number, message: string): string {
  if (code === 404 || /not found|no handler|no endpoint|未注册/i.test(message)) {
    return '供应商能力未启用（后端能力开关未打开或端口未就绪）';
  }
  if (code === 403 || /forbidden|denied|无权限/i.test(message)) {
    return '无权限：需要供应商治理读写权限';
  }
  return message || `请求失败（code=${code}）`;
}

/**
 * 把 apiClient 拦截器 reject 的错误归一为 {code, message}，供页面 try/catch 消费。
 *
 * apiClient 对两类失败一律 Promise.reject：
 * 1) HTTP 200 + success:false → Error with displayMessage（业务失败，如身份冲突/守卫拒绝）；
 * 2) HTTP 403/404/5xx → axios Error with response.status（能力未启用/无权限/服务端失败）。
 * 因此页面不能用 `if (!resp.success)` 判失败（那是死代码），必须 try/catch 后调用本函数。
 */
export function fromApiError(error: unknown): { code: number; message: string } {
  const e = error as {
    displayMessage?: string;
    message?: string;
    response?: { status?: number; data?: { message?: string } };
  } | null;
  const code = e?.response?.status ?? 0;
  const message =
    e?.displayMessage ??
    e?.response?.data?.message ??
    e?.message ??
    '请求失败，请稍后重试';
  return { code, message };
}

/** 分页页数：total 可能为字符串（Long→String），先归一。 */
export function pageCount(total: string | number | undefined, size: number): number {
  const n = normalizeTotal(total);
  if (n === 0 || size <= 0) return 0;
  return Math.ceil(n / size);
}

/**
 * 安全投影：只保留白名单字段，剔除任何意外出现的 PII / 身份主键字段。
 * 未知状态码原样保留，不改写为已知态。
 */
export function safeStateProjection(raw: SupplierStateDTO | Record<string, unknown>): SupplierStateDTO {
  const r = raw as unknown as Record<string, unknown>;
  return {
    partyId: typeof r.partyId === 'string' ? r.partyId : '',
    supplierState: typeof r.supplierState === 'string' ? r.supplierState : String(r.supplierState ?? ''),
    supplierVersion: Number(r.supplierVersion ?? 0),
    vendorState: typeof r.vendorState === 'string' ? r.vendorState : String(r.vendorState ?? ''),
    vendorVersion: Number(r.vendorVersion ?? 0),
    supplierHistory: Array.isArray(r.supplierHistory) ? r.supplierHistory : [],
    vendorHistory: Array.isArray(r.vendorHistory) ? r.vendorHistory : [],
  };
}

/** 迁移摘要原样呈现；缺失/畸形显示占位，不推断为正常。 */
export function formatHistory(history: unknown[]): string[] {
  return history.map((v) => {
    if (typeof v === 'string' && v.trim()) return v;
    if (v === null || v === undefined) return '(无摘要)';
    if (typeof v === 'object') return '(摘要格式异常)';
    return String(v);
  });
}
