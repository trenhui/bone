import type { ApiResponse, PageResult } from '@bone/shared-types';

/**
 * 交易域（blueprint）契约类型。
 *
 * 全部字段形态以**后端实测响应**为准（2026-10-03，`GET /api/v1/orders`）：
 * - 雪花 ID（`id` / `customerId` / `productId`）经全局 Long→String 序列化后是**字符串**，
 *   禁止 `Number()` 转换（超出 2^53 会被静默截断）。
 * - 金额（`totalAmount` 等）是 JSON number（BigDecimal，非 Long）。
 * - 时间字段是 ISO-8601 带 `Z` 偏移的字符串（服务端 `Instant`）。
 * - 分页出参权威字段是 `records`；`total` 是**字符串**（Long→String），
 *   `page`/`size`/`pages` 是 number。注意出参没有 `pageNum`/`pageSize` 键，
 *   入参才用 `pageNum`/`pageSize`（blueprint 与 system/metadata 同属第二族）。
 */
export type { ApiResponse, PageResult };

/** 订单状态机：CREATED → PAID → SHIPPED → DELIVERED，旁路 CANCELLED / REFUNDED。 */
export type OrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

/** 支付单状态机。 */
export type PaymentStatus = 'PENDING' | 'PAYING' | 'SUCCESS' | 'FAILED' | 'CLOSED';

export interface OrderItem {
  id?: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal?: number;
}

export interface OrderSummary {
  id: string;
  customerId: string;
  totalAmount: number;
  status: OrderStatus;
  createdAt: string;
  /** 订单来源渠道码（字典 source_channel：WEB / APP / MINI） */
  channelSource?: string | null;
  /** 渠道中文名（读路径由字典解析，字典不可达时为空） */
  channelSourceName?: string | null;
}

export interface OrderDetail extends OrderSummary {
  /** 关联支付单号（雪花 ID，字符串）。订单发起支付后随详情返回，可在订单详情常驻定位支付单。 */
  paymentId?: string | null;
  items: OrderItem[];
}

export interface PaymentDetail {
  paymentId: string;
  orderId: string;
  customerId: string;
  amount: number;
  channel?: string | null;
  status: PaymentStatus;
  channelTradeNo?: string | null;
  payUrl?: string | null;
  paidAt?: string | null;
  refundedAt?: string | null;
  refundAmount?: number | null;
  createdAt: string;
}

export interface CreateOrderItemReq {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateOrderReq {
  customerId: string;
  items: CreateOrderItemReq[];
  /** 来源渠道码；非空时服务端校验是否命中字典 source_channel 已启用选项 */
  channelSource?: string;
}

export interface CreateOrderResp {
  id: string;
}

export interface InitiatePaymentReq {
  orderId: string;
}

export interface InitiatePaymentResp {
  paymentId: string;
  payUrl?: string | null;
}

export interface RefundPaymentReq {
  refundAmount: number;
}

export interface OrderPageQuery {
  customerId?: string;
  status?: OrderStatus | '';
  pageNum: number;
  pageSize: number;
}
