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

  /** 下单商品不是已发布的商品主数据记录（主数据治理前置校验失败）。 */
  public static final String ORDER_PRODUCT_NOT_PUBLISHED = "BP_ORDER_PRODUCT_NOT_PUBLISHED";

  /** 订单来源渠道码非法：不在字典 source_channel 已启用的选项内（字典强校验失败）。 */
  public static final String ORDER_CHANNEL_SOURCE_INVALID = "BP_ORDER_CHANNEL_SOURCE_INVALID";

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

  // ===== 多渠道交易（BP_CHANNEL_* / BP_INVENTORY_* / BP_SHIPMENT_*）=====

  /** 渠道不存在（含跨租户不可见）或渠道码未登记。 */
  public static final String CHANNEL_NOT_FOUND = "BP_CHANNEL_NOT_FOUND";

  /** 渠道码非法：不是已接入的渠道（TAOBAO/JD/DOUYIN/PDD）。 */
  public static final String CHANNEL_CODE_INVALID = "BP_CHANNEL_CODE_INVALID";

  /** 渠道已停用：停用渠道不参与拉单/上架/发货。 */
  public static final String CHANNEL_DISABLED = "BP_CHANNEL_DISABLED";

  /** 渠道商品（内部商品 × 渠道的上架关系）不存在。 */
  public static final String CHANNEL_PRODUCT_NOT_FOUND = "BP_CHANNEL_PRODUCT_NOT_FOUND";

  /** 渠道商品当前状态不允许该操作（如对未上架商品发起下架）。 */
  public static final String CHANNEL_PRODUCT_STATUS_CONFLICT = "BP_CHANNEL_PRODUCT_STATUS_CONFLICT";

  /** 渠道侧拒绝了上架/下架/同步请求（协议层失败，非系统故障）。 */
  public static final String CHANNEL_PRODUCT_REJECTED = "BP_CHANNEL_PRODUCT_REJECTED";

  /** 渠道开放平台凭证缺失（未配置 appKey/appSecret 即走真实通道）。 */
  public static final String CHANNEL_CREDENTIAL_MISSING = "BP_CHANNEL_CREDENTIAL_MISSING";

  /** 渠道开放平台调用失败（网络/超时/解析异常，重试通常有效）。 */
  public static final String CHANNEL_OPENAPI_FAILED = "BP_CHANNEL_OPENAPI_FAILED";

  /** 渠道侧开放平台拒绝了本次调用（已送达被拒，重试无意义）。 */
  public static final String CHANNEL_OPENAPI_REJECTED = "BP_CHANNEL_OPENAPI_REJECTED";

  /** 渠道买家映射不存在（未绑定内部客户，且未开启自动建影子客户）。 */
  public static final String CHANNEL_BUYER_NOT_FOUND = "BP_CHANNEL_BUYER_NOT_FOUND";

  /** 渠道买家已绑定其它内部客户（重复绑定会污染订单归属）。 */
  public static final String CHANNEL_BUYER_ALREADY_BOUND = "BP_CHANNEL_BUYER_ALREADY_BOUND";

  /** 渠道广播任务不存在。 */
  public static final String CHANNEL_BROADCAST_NOT_FOUND = "BP_CHANNEL_BROADCAST_NOT_FOUND";

  /** 库存记录不存在（商品未在该仓库建库存）。 */
  public static final String INVENTORY_NOT_FOUND = "BP_INVENTORY_NOT_FOUND";

  /** 库存不足：可用量小于需求量——超卖的唯一拦截点。 */
  public static final String INVENTORY_INSUFFICIENT = "BP_INVENTORY_INSUFFICIENT";

  /** 发货单不存在。 */
  public static final String SHIPMENT_NOT_FOUND = "BP_SHIPMENT_NOT_FOUND";

  /** 发货单状态不允许该操作。 */
  public static final String SHIPMENT_STATUS_CONFLICT = "BP_SHIPMENT_STATUS_CONFLICT";

  /** 发货必须提供运单号——没有运单号的「已发货」是虚假发货。 */
  public static final String SHIPMENT_TRACKING_NO_REQUIRED = "BP_SHIPMENT_TRACKING_NO_REQUIRED";

  // ===== 幂等（复用平台公共码，不另造 BP_ 码）=====

  private BlueprintErrorCodes() {}
}
