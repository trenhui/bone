package com.bone.blueprint.common;

/**
 * bone-blueprint 业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md} § BP_）。
 *
 * <p><b>为何需要这一层，而不是直接写中文消息</b>：{@code throw new BizException("xxx失败")} 让前端、监控、告警 只能按中文 message
 * 分类——文案一改，聚合口径即断（错误码登记 §2「可聚合」「可 i18n」）。稳定码是跨系统契约， 中文说明只是 fallback。
 *
 * <p><b>用法</b>：{@code throw new BizException(409, BlueprintErrorCodes.ORDER_STATUS_CONFLICT + ": "
 * + orderId)}。首个参数是 HTTP 状态（{@code GlobalExceptionHandler} 用它设置响应状态，非 400–599 的值会被 兜底成
 * 400），故错误码常量必须与状态成对使用——参见下方 {@code XXX_STATUS} 常量。
 *
 * <p><b>命名</b>：{@code BP_} 为蓝图模块前缀，格式 {@code {DOMAIN_PREFIX}_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。
 */
public final class BlueprintErrorCodes {

  // ===== 订单（BP_ORDER_*）=====

  /** 订单不存在（404）。 */
  public static final String ORDER_NOT_FOUND = "BP_ORDER_NOT_FOUND";

  /** 订单状态不允许该操作，如对非待支付订单发起支付（409）。 */
  public static final String ORDER_STATUS_CONFLICT = "BP_ORDER_STATUS_CONFLICT";

  /** 订单状态查询入参非法（400）。 */
  public static final String ORDER_STATUS_INVALID = "BP_ORDER_STATUS_INVALID";

  // ===== 支付（BP_PAYMENT_*）=====

  /** 支付单不存在（404）。 */
  public static final String PAYMENT_NOT_FOUND = "BP_PAYMENT_NOT_FOUND";

  /** 支付单状态不允许该操作（409）。 */
  public static final String PAYMENT_STATUS_CONFLICT = "BP_PAYMENT_STATUS_CONFLICT";

  /** 渠道回调签名校验失败（401）——不可信调用方，不得进入状态机。 */
  public static final String PAYMENT_SIGNATURE_INVALID = "BP_PAYMENT_SIGNATURE_INVALID";

  /** 渠道预下单失败（502）——上游依赖故障，重试前先确认渠道侧是否已受理。 */
  public static final String PAYMENT_CHANNEL_PREPAY_FAILED = "BP_PAYMENT_CHANNEL_PREPAY_FAILED";

  // ===== 幂等（复用平台公共码，不另造 BP_ 码）=====

  /** 同一 Idempotency-Key 被用于不同请求体（409）——见错误码登记 §6 `COMMON_`。 */
  public static final String IDEMPOTENCY_CONFLICT = "COMMON_IDEMPOTENCY_CONFLICT";

  private BlueprintErrorCodes() {}
}
