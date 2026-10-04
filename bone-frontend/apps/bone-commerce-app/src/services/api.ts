import { createApiClient, setQiankunToken } from '@bone/shared-services';
import type { ApiResponse, PageResult } from '@bone/shared-types';
import type {
  CreateOrderReq,
  CreateOrderResp,
  InitiatePaymentReq,
  InitiatePaymentResp,
  OrderDetail,
  OrderPageQuery,
  OrderSummary,
  PaymentDetail,
  RefundPaymentReq,
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
