package com.bone.blueprint.common;

/**
 * bone-blueprint 业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md} § BP_）。
 *
 * <p><b>为何需要这一层，而不是直接写中文消息</b>：{@code throw new BizException("xxx失败")} 让前端、监控、告警 只能按中文 message
 * 分类——文案一改，聚合口径即断（错误码登记 §2「可聚合」「可 i18n」）。稳定码是跨系统契约， 中文说明只是 fallback。
 *
 * <p><b>用法</b>：抛出走 {@link BlueprintErrors#of(String, Object)}——{@code throw
 * BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_CONFLICT, orderId)}。HTTP 状态由 {@link
 * BlueprintErrors} 的「码 → 状态」表提供，<strong>不要</strong>在抛出点再手写状态数字：状态与码各写一处即会漂移， 且不一致时无任何机制发现。
 *
 * <p><b>本类只承载「稳定的业务码字符串 + 语义」</b>，不承载状态（真源见 {@link BlueprintErrors}），也不承载 文案（文案由抛出点补充，便于携带业务标识）。
 *
 * <p><b>命名</b>：{@code BP_} 为蓝图模块前缀，格式 {@code {DOMAIN_PREFIX}_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。
 */
public final class BlueprintErrorCodes {

  // ===== 订单（BP_ORDER_*）=====

  /** 订单不存在（含跨租户不可见）。 */
  public static final String ORDER_NOT_FOUND = "BP_ORDER_NOT_FOUND";

  /** 订单状态不允许该操作，如对非待支付订单发起支付。 */
  public static final String ORDER_STATUS_CONFLICT = "BP_ORDER_STATUS_CONFLICT";

  /** 订单状态查询入参非法（不是合法的 OrderStatus 枚举名）。 */
  public static final String ORDER_STATUS_INVALID = "BP_ORDER_STATUS_INVALID";

  /** 下单商品库存不足（同步预校验失败）。 */
  public static final String ORDER_STOCK_INSUFFICIENT = "BP_ORDER_STOCK_INSUFFICIENT";

  // ===== 支付（BP_PAYMENT_*）=====

  /** 支付单不存在（含跨租户不可见）。 */
  public static final String PAYMENT_NOT_FOUND = "BP_PAYMENT_NOT_FOUND";

  /** 支付单状态不允许该操作。 */
  public static final String PAYMENT_STATUS_CONFLICT = "BP_PAYMENT_STATUS_CONFLICT";

  /** 渠道回调签名校验失败——不可信调用方，不得进入状态机。 */
  public static final String PAYMENT_SIGNATURE_INVALID = "BP_PAYMENT_SIGNATURE_INVALID";

  /** 渠道预下单失败——上游依赖故障，重试前先确认渠道侧是否已受理。 */
  public static final String PAYMENT_CHANNEL_PREPAY_FAILED = "BP_PAYMENT_CHANNEL_PREPAY_FAILED";

  /** 渠道回调来源不在白名单——纵深防御：仅允许受信任的支付网关来源命中白名单。 */
  public static final String PAYMENT_CALLBACK_SOURCE_NOT_ALLOWED =
      "BP_PAYMENT_CALLBACK_SOURCE_NOT_ALLOWED";

  // ===== 幂等（复用平台公共码，不另造 BP_ 码）=====

  /** 同一 Idempotency-Key 被用于不同请求体——见错误码登记 §6 {@code COMMON_}。 */
  public static final String IDEMPOTENCY_CONFLICT = "COMMON_IDEMPOTENCY_CONFLICT";

  private BlueprintErrorCodes() {}
}
