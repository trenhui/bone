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
 *   `page`/`size`/`pages` 是 number。出参与入参现已统一为 `records`/`page`/`size`
 *   （`page` 为 1-based，首页 = 1），不再有 `pageNum`/`pageSize` 第二族。
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
  page: number;
  size: number;
}

// ==================== 多渠道交易域 ====================

/** 销售渠道码；与后端 ChannelCode 枚举一一对应。 */
export type ChannelCode = 'TAOBAO' | 'JD' | 'DOUYIN' | 'PDD';

/** 渠道出参。 */
export interface ChannelSummary {
  /** 雪花 ID 字符串（全局 Long→String 序列化），禁止 Number() 转换。 */
  id: string;
  channelCode: string;
  channelName: string;
  extImplCode: string | null;
  apiEndpoint: string | null;
  enabled: boolean;
  orderSyncEnabled: boolean;
  lastSyncAt: string | null;
  remark: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

/** 商品在渠道的上架状态机。 */
export type ListingStatus =
  | 'UNLISTED'
  | 'LISTING'
  | 'ONLINE'
  | 'DELISTING'
  | 'OFFLINE'
  | 'FAILED';

export interface ChannelProductSummary {
  id: string;
  channelCode: string;
  /** 内部商品 ID，字符串（雪花 ID）。 */
  productId: string;
  productName: string;
  /** 渠道侧商品 ID，上架成功后回填。 */
  channelProductId: string | null;
  listingStatus: ListingStatus;
  listingPrice: number | null;
  listingStock: number;
  lastSyncAt: string | null;
  failReason: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface InventorySummary {
  id: string;
  productId: string;
  productName: string | null;
  warehouseCode: string;
  availableQty: number;
  reservedQty: number;
  safetyStock: number;
  lowStock: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export type ReplenishmentStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'RECEIVED'
  | 'CANCELLED';

export interface ReplenishmentSuggestion {
  productId: string;
  productName: string | null;
  warehouseCode: string;
  availableQty: number;
  safetyStock: number;
  suggestedQty: number;
  hasOpenOrder: boolean;
}

export interface ReplenishmentOrderSummary {
  id: string;
  replenishNo: string;
  productId: string;
  productName: string | null;
  warehouseCode: string;
  quantity: number;
  suggestedQty: number | null;
  availableSnapshot: number | null;
  safetySnapshot: number | null;
  supplierCode: string | null;
  status: ReplenishmentStatus;
  remark: string | null;
  submittedAt: string | null;
  approvedAt: string | null;
  receivedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export type ShipmentStatus =
  | 'CREATED'
  | 'SHIPPED'
  | 'IN_TRANSIT'
  | 'SIGNED'
  | 'FAILED';

export interface ShipmentSummary {
  id: string;
  orderId: string;
  channelCode: string | null;
  shipmentNo: string | null;
  logisticsCompany: string | null;
  trackingNo: string | null;
  status: ShipmentStatus;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverAddress: string | null;
  /** 是否已回传渠道；false 且状态已 SHIPPED 表示「已发货但渠道未同步」。 */
  channelAck: boolean;
  channelAckAt: string | null;
  shippedAt: string | null;
  signedAt: string | null;
  failReason: string | null;
  remark: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ShipmentTrace {
  id: string;
  shipmentId: string;
  traceTime: string;
  traceStatus: string | null;
  traceDesc: string;
}

/** 渠道订单拉取请求（模拟渠道推送报文）。 */
export interface PullChannelOrderReq {
  channelCode: string;
  channelOrderNo: string;
  buyerNick?: string;
  payAmount?: number;
  receiverName?: string;
  receiverPhone?: string;
  receiverAddress?: string;
  lines: {
    /** 渠道 SKU 编码：TB/JD/DY/PDD + 内部商品ID */
    outerSkuId: string;
    title?: string;
    quantity: number;
    unitPrice: number;
  }[];
}

/**
 * 渠道买家映射（渠道买家账号 ↔ 内部客户）。
 *
 * 全部 ID 为字符串：后端全局 Long→String，雪花 ID 超 2^53，JS Number 会丢精度。
 * `customerId === '0'` 表示影子映射（尚未绑定内部客户），不是真实客户。
 */
export interface ChannelBuyerSummary {
  id: string;
  channelCode: string;
  channelBuyerId: string;
  channelBuyerNick: string | null;
  customerId: string;
  customerName: string | null;
  /** 是否已绑定到真实内部客户。 */
  bound: boolean;
  bindingSource: 'MANUAL' | 'AUTO_SHADOW' | string;
  bindingSourceName: string;
  orderCount: number | null;
  firstSeenAt: string | null;
  lastOrderAt: string | null;
  remark: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

/**
 * 库存广播任务（Outbox 异步投递）。
 *
 * `targetStock` 是**覆盖值**而非增量：渠道库存接口都是「设置为某个数」。
 * `lastError` 是渠道原始拒绝原因，排查时直接看它而不是猜。
 */
export interface BroadcastTaskSummary {
  id: string;
  productId: string | null;
  channelCode: string;
  channelProductId: string | null;
  productName: string | null;
  targetStock: number | null;
  status: 'PENDING' | 'PROCESSING' | 'SENT' | 'FAILED' | string;
  retryCount: number | null;
  nextRetryAt: string | null;
  lastError: string | null;
  /** 被合并到的目标任务 ID：同商品同渠道只投最新库存，旧任务标 SENT 并记此字段。 */
  mergedIntoId: string | null;
  createdAt: string | null;
}
