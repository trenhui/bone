import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type { ApiResponse, PageResult } from '@bone/shared-types';
import type {
  BroadcastTaskSummary,
  ChannelBuyerSummary,
  ChannelProductSummary,
  ChannelSummary,
  CreateOrderReq,
  CreateOrderResp,
  InitiatePaymentReq,
  InitiatePaymentResp,
  InventorySummary,
  OrderDetail,
  OrderPageQuery,
  OrderSummary,
  PaymentDetail,
  PullChannelOrderReq,
  RefundPaymentReq,
  ShipmentSummary,
  ShipmentTrace,
} from '../types';

export { setQiankunToken };

/**
 * 订单根路径常量。
 *
 * 为什么用常量而不是散落字面量：网关路由登记的是 `/api/v1/orders/**` 与
 * `/api/v1/payments/**`（见 gateway `application.yml`），路径一旦漂移必须成组改，
 * 散落字面量会让「改一半」成为静默缺陷。
 */
const ORDERS = '/api/v1/orders';
const PAYMENTS = '/api/v1/payments';

const api = createApiClient('', { timeout: 15000 });

/**
 * 创建订单支持 `Idempotency-Key` 幂等写。
 *
 * 为什么由调用方生成并透传：同键 + 同请求体重提交返回同一响应；同键 + 不同请求体返回
 * 409 COMMON_IDEMPOTENCY_CONFLICT。若每个「新建订单」点击都复用同一个键，重复提交
 * 会被键值对折叠；若完全不带键，则网络重试会造成重复下单——这里每次点击生成 UUID 是
 * 唯一正确姿势。
 */
function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  // 兜底：非安全上下文（http 且非 localhost）下 crypto.randomUUID 不可用
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const orderApi = {
  page: (params: OrderPageQuery): Promise<ApiResponse<PageResult<OrderSummary>>> =>
    api.get<never, ApiResponse<PageResult<OrderSummary>>>(ORDERS, { params }),

  detail: (id: string): Promise<ApiResponse<OrderDetail>> =>
    api.get<never, ApiResponse<OrderDetail>>(`${ORDERS}/${id}`),

  create: (data: CreateOrderReq): Promise<ApiResponse<CreateOrderResp>> =>
    api.post<never, ApiResponse<CreateOrderResp>>(ORDERS, data, {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    }),

  ship: (id: string): Promise<ApiResponse<void>> =>
    api.post<never, ApiResponse<void>>(`${ORDERS}/${id}/ship`),

  deliver: (id: string): Promise<ApiResponse<void>> =>
    api.post<never, ApiResponse<void>>(`${ORDERS}/${id}/deliver`),

  cancel: (id: string): Promise<ApiResponse<void>> =>
    api.post<never, ApiResponse<void>>(`${ORDERS}/${id}/cancel`),
};

export const paymentApi = {
  initiate: (data: InitiatePaymentReq): Promise<ApiResponse<InitiatePaymentResp>> =>
    api.post<never, ApiResponse<InitiatePaymentResp>>(`${PAYMENTS}/initiate`, data),

  detail: (paymentId: string): Promise<ApiResponse<PaymentDetail>> =>
    api.get<never, ApiResponse<PaymentDetail>>(`${PAYMENTS}/${paymentId}`),

  refund: (paymentId: string, data: RefundPaymentReq): Promise<ApiResponse<void>> =>
    api.post<never, ApiResponse<void>>(`${PAYMENTS}/${paymentId}/refund`, data),
};

/**
 * 支付回调为何不在前端提供：
 *
 * `POST /api/v1/payments/callback` 是支付渠道的 server-to-server 端点——恒签名校验
 * （HMAC-SHA256，密钥只存在于服务端）+ 来源 IP 白名单。前端若持有密钥做「模拟回调」，
 * 等于把密钥下发到浏览器，任何使用者都能伪造任意金额的支付成功通知。
 *
 * 因此 UI 只做到「发起支付 → 展示支付链接 → 查询支付单状态」，状态推进由真实渠道回调完成；
 * 联调/回归中这一步由 `scripts/e2e/chains.py` 以服务端视角构造合法签名回调覆盖。
 */

// ==================== 多渠道交易域 API ====================
//
// 路径常量集中在此的原因与 orders/payments 相同：网关登记的是 `/api/v1/channels/**`
// 等前缀，路径漂移必须成组改，散落字面量会让「改一半」成为静默缺陷。

const CHANNELS = '/api/v1/channels';
const CHANNEL_PRODUCTS = '/api/v1/channel-products';
const CHANNEL_ORDERS = '/api/v1/channel-orders';
const INVENTORIES = '/api/v1/inventories';
const SHIPMENTS = '/api/v1/shipments';

export const channelApi = {
  page: (params: { channelCode?: string; page: number; size: number }) =>
    api.get<never, ApiResponse<PageResult<ChannelSummary>>>(CHANNELS, { params }),

  enable: (channelCode: string) =>
    api.post<never, ApiResponse<ChannelSummary>>(`${CHANNELS}/${channelCode}/enable`),

  disable: (channelCode: string) =>
    api.post<never, ApiResponse<ChannelSummary>>(`${CHANNELS}/${channelCode}/disable`),

  setOrderSync: (channelCode: string, enabled: boolean) =>
    api.post<never, ApiResponse<ChannelSummary>>(
      `${CHANNELS}/${channelCode}/order-sync`,
      null,
      { params: { enabled } },
    ),
};

export const channelProductApi = {
  page: (params: {
    channelCode?: string;
    productId?: string;
    listingStatus?: string;
    page: number;
    size: number;
  }) =>
    api.get<never, ApiResponse<PageResult<ChannelProductSummary>>>(CHANNEL_PRODUCTS, {
      params,
    }),

  list: (data: {
    channelCode: string;
    productId: string;
    productName: string;
    listingPrice: number;
  }) => api.post<never, ApiResponse<ChannelProductSummary>>(CHANNEL_PRODUCTS, data),

  delist: (data: { channelCode: string; productId: string }) =>
    api.post<never, ApiResponse<ChannelProductSummary>>(`${CHANNEL_PRODUCTS}/delist`, data),

  /** 广播库存到全部已上架渠道（多渠道共享实物库存，只同步一个渠道会超卖）。 */
  syncInventory: (productId: string, stock: number) =>
    api.post<never, ApiResponse<ChannelProductSummary[]>>(
      `${CHANNEL_PRODUCTS}/sync-inventory`,
      null,
      { params: { productId, stock } },
    ),
};

export const channelOrderApi = {
  /** 拉取渠道订单并落单（服务端按渠道原始订单号幂等）。 */
  pull: (data: PullChannelOrderReq) =>
    api.post<never, ApiResponse<string>>(`${CHANNEL_ORDERS}/pull`, data),

  ack: (channelCode: string, channelOrderNo: string) =>
    api.post<never, ApiResponse<boolean>>(
      `${CHANNEL_ORDERS}/${channelCode}/${channelOrderNo}/ack`,
    ),
};

export const inventoryApi = {
  page: (params: {
    productId?: string;
    lowStockOnly?: boolean;
    page: number;
    size: number;
  }) => api.get<never, ApiResponse<PageResult<InventorySummary>>>(INVENTORIES, { params }),

  receive: (data: {
    productId: string;
    productName?: string;
    warehouseCode?: string;
    quantity: number;
  }) => api.post<never, ApiResponse<InventorySummary>>(`${INVENTORIES}/receive`, data),

  deduct: (data: { productId: string; warehouseCode?: string; quantity: number }) =>
    api.post<never, ApiResponse<InventorySummary>>(`${INVENTORIES}/deduct`, data),

  reserve: (data: { productId: string; warehouseCode?: string; quantity: number }) =>
    api.post<never, ApiResponse<InventorySummary>>(`${INVENTORIES}/reserve`, data),

  confirm: (data: { productId: string; warehouseCode?: string; quantity: number }) =>
    api.post<never, ApiResponse<InventorySummary>>(`${INVENTORIES}/confirm`, data),

  release: (data: { productId: string; warehouseCode?: string; quantity: number }) =>
    api.post<never, ApiResponse<InventorySummary>>(`${INVENTORIES}/release`, data),

  setSafetyStock: (data: {
    productId: string;
    warehouseCode?: string;
    quantity: number;
  }) => api.post<never, ApiResponse<InventorySummary>>(`${INVENTORIES}/safety-stock`, data),
};

export const shipmentApi = {
  page: (params: {
    channelCode?: string;
    status?: string;
    page: number;
    size: number;
  }) => api.get<never, ApiResponse<PageResult<ShipmentSummary>>>(SHIPMENTS, { params }),

  create: (data: {
    orderId: string;
    channelCode?: string;
    receiverName?: string;
    receiverPhone?: string;
    receiverAddress?: string;
  }) => api.post<never, ApiResponse<ShipmentSummary>>(SHIPMENTS, data),

  ship: (shipmentId: string, data: { logisticsCompany?: string; trackingNo: string }) =>
    api.post<never, ApiResponse<ShipmentSummary>>(`${SHIPMENTS}/${shipmentId}/ship`, data),

  sign: (shipmentId: string) =>
    api.post<never, ApiResponse<ShipmentSummary>>(`${SHIPMENTS}/${shipmentId}/sign`),

  syncTrace: (shipmentId: string) =>
    api.post<never, ApiResponse<ShipmentTrace[]>>(
      `${SHIPMENTS}/${shipmentId}/traces/sync`,
    ),

  traces: (shipmentId: string) =>
    api.get<never, ApiResponse<ShipmentTrace[]>>(`${SHIPMENTS}/${shipmentId}/traces`),
};

// ==================== 渠道买家映射 + 库存广播任务 ====================

const CHANNEL_BUYERS = '/api/v1/channel-buyers';
const CHANNEL_BROADCASTS = '/api/v1/channel-broadcasts';

/**
 * 「渠道买家 ↔ 内部客户」映射 API。
 *
 * 绑定/改绑/解绑分三个端点而不是一个带 force 的端点：改绑会改变历史订单的客户归属，
 * 混在一起会让「顺手改绑」变得容易发生，而这类错误要等对账差异出来才被发现。
 */
export const channelBuyerApi = {
  page: (params: { channelCode?: string; bound?: boolean; page: number; size: number }) =>
    api.get<never, ApiResponse<PageResult<ChannelBuyerSummary>>>(CHANNEL_BUYERS, { params }),

  /** 待绑定影子清单（按最近拉单时间倒序）：运营优先处理高频渠道买家。 */
  shadowCandidates: (channelCode?: string, limit = 50) =>
    api.get<never, ApiResponse<ChannelBuyerSummary[]>>(CHANNEL_BUYERS + '/shadow-candidates', {
      params: { channelCode, limit },
    }),

  bind: (data: {
    channelCode: string;
    channelBuyerId: string;
    customerId: string;
    customerName?: string;
  }) => api.post<never, ApiResponse<ChannelBuyerSummary>>(CHANNEL_BUYERS + '/bind', null, { params: data }),

  rebind: (data: {
    channelCode: string;
    channelBuyerId: string;
    customerId: string;
    customerName?: string;
    reason: string;
  }) => api.post<never, ApiResponse<ChannelBuyerSummary>>(CHANNEL_BUYERS + '/rebind', null, { params: data }),

  unbind: (data: { channelCode: string; channelBuyerId: string }) =>
    api.post<never, ApiResponse<ChannelBuyerSummary>>(CHANNEL_BUYERS + '/unbind', null, {
      params: data,
    }),
};

/** 库存广播任务（Outbox）运维 API。 */
export const channelBroadcastApi = {
  page: (params: { status?: string; page: number; size: number }) =>
    api.get<never, ApiResponse<PageResult<BroadcastTaskSummary>>>(CHANNEL_BROADCASTS, {
      params,
    }),

  /** 人工重试：回队列并立即推进一轮中继。 */
  retry: (taskId: string) =>
    api.post<never, ApiResponse<BroadcastTaskSummary>>(`${CHANNEL_BROADCASTS}/${taskId}/retry`),

  /** 立即推进一轮中继（不等下一个调度周期）。 */
  relayNow: () => api.post<never, ApiResponse<number[]>>(`${CHANNEL_BROADCASTS}/relay-now`),
};
