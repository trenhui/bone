package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.Map;

/**
 * 渠道开放平台调用结果 —— 归一化后的<strong>平台侧</strong>结果，与渠道协议解耦。
 *
 * <p><b>为何要有「成功但 data 为空」这一态</b>：渠道的成功/失败判定在响应体里，各渠道字段名、层级都不同（淘宝 {@code error_response}、抖音 {@code
 * code}、拼多多嵌套两层）。 解析后统一成本类型，扩展实现只面对 {@code success + data}，不必再写第四遍错误判定。
 *
 * <p><b>{@code data} 为 null 是合法语义</b>：表示「渠道受理成功但没有返回业务体」（拉单接口查不到订单、下单接口只回受理号等），
 * 由扩展实现自行决定是回落兜底数据还是抛业务错——<strong>是否容忍空响应是渠道的业务语义，不该由传输层替它决定</strong>。
 *
 * @param success 渠道侧是否受理成功
 * @param data 业务体（成功但无业务体时为 {@code null}）
 * @param errorCode 渠道错误码（成功时为 {@code null}）
 * @param errorMessage 渠道错误文案（成功时为 {@code null}）
 * @param rawBody 原始响应，仅用于排障日志（截断）
 */
public record ChannelApiResult(
    boolean success,
    Map<String, Object> data,
    String errorCode,
    String errorMessage,
    String rawBody) {

  /** 成功且带业务体。 */
  public static ChannelApiResult ok(Map<String, Object> data, String rawBody) {
    return new ChannelApiResult(true, data, null, null, rawBody);
  }

  /** 成功但渠道未返回业务体（显式区分于「调用失败」）。 */
  public static ChannelApiResult empty(String rawBody) {
    return new ChannelApiResult(true, null, null, null, rawBody);
  }

  /** 渠道显式拒绝（协议层业务错误）。 */
  public static ChannelApiResult rejected(String errorCode, String errorMessage, String rawBody) {
    return new ChannelApiResult(false, null, errorCode, errorMessage, rawBody);
  }

  /**
   * 是否为「鉴权失效」类错误。
   *
   * <p>需要单独识别的原因：这类错误<strong>重试无意义，但刷新令牌后可以重试一次</strong>。 把「令牌过期」当成普通业务拒绝处理，
   * 会导致每一次令牌轮换都产生一批渠道侧拒绝记录，掩盖真正的业务问题。
   */
  public boolean isAuthError() {
    if (success) {
      return false;
    }
    String code = (errorCode == null ? "" : errorCode).toUpperCase();
    String message = (errorMessage == null ? "" : errorMessage).toUpperCase();
    return code.contains("401")
        || code.contains("TOKEN")
        || code.contains("AUTH")
        || message.contains("TOKEN")
        || message.contains("ACCESS")
        || message.contains("签名") && message.contains("失败");
  }

  @Override
  public String toString() {
    return "ChannelApiResult{success="
        + success
        + ", errorCode="
        + errorCode
        + ", errorMessage="
        + errorMessage
        + ", dataSize="
        + (data == null ? 0 : data.size())
        + '}';
  }
}
