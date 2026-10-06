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
   * <p>需要单独识别的原因：这类错误<strong>重试无意义，但刷新令牌后可以重试一次</strong>。把「令牌过期」当成普通业务拒绝处理，
   * 会导致每一次令牌轮换都产生一批渠道侧拒绝记录，掩盖真正的业务问题。
   *
   * <p><b>判定口径（2026-10-06 修正）</b>：原实现用 {@code code.contains("401")} 之类子串匹配， 有两个误判面——
   *
   * <ul>
   *   <li><b>子串误判</b>：{@code "40101"}、{@code "1401"}、{@code "401001"} 都会 {@code contains("401")}
   *       命中， 于是「库存不足 401001」这类普通业务错误被当成令牌失效；反过来渠道返回 {@code "UNAUTHORIZED"} 时反而识别不出。
   *   <li><b>中英混判</b>：{@code message.contains("TOKEN")} 是英文判定，与 {@code contains("签名") &&
   *       contains("失败")} 的中文判定混在一起，既漏「invalid signature」这类纯英文文案，又对中文「签名失败」要求两个字同时出现。
   * </ul>
   *
   * <p>现改为：<b>错误码精确匹配已知鉴权码 + 文案按关键词集合匹配（不要求相邻）</b>。
   * 关键词表可按渠道实际文案补充，但走的是「明确列举」而非模糊子串，宁可漏判（退回普通拒绝、行为不变） 也不误判（把业务错误当鉴权问题、误导运维去刷令牌）。
   */
  public boolean isAuthError() {
    if (success) {
      return false;
    }
    String code = errorCode == null ? "" : errorCode.trim().toUpperCase();
    if (AUTH_ERROR_CODES.contains(code)) {
      return true;
    }
    String message = (errorMessage == null ? "" : errorMessage).trim().toLowerCase();
    for (String keyword : AUTH_MESSAGE_KEYWORDS) {
      if (message.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  /** 各平台约定的鉴权失效错误码（精确匹配，不做子串判断）。 */
  private static final java.util.Set<String> AUTH_ERROR_CODES =
      java.util.Set.of(
          "401",
          "40100",
          "INVALID_TOKEN",
          "INVALID_SESSION",
          "UNAUTHORIZED",
          "UNAUTHORIZED_ERROR",
          "NO_AUTH",
          "TOKEN_EXPIRED",
          "TOKEN_INVALID",
          "SIGNATURE_INVALID",
          "SIGN_ERROR");

  /**
   * 鉴权失效的文案关键词（任一命中即可，不要求相互邻近）。
   *
   * <p>刻意<b>同时列举中英文</b>：早期版本只匹配大写英文（{@code TOKEN}），而淘宝/拼多多回的是中文文案，
   * 于是「签名错误」这类真正的令牌问题被判成普通业务拒绝；补中文后又漏了 {@code invalid signature}。
   * 两类文案都会出现，按<b>明确列举</b>而非模糊子串——宁可漏判（退回普通拒绝，行为不变）也不误判。
   */
  private static final java.util.List<String> AUTH_MESSAGE_KEYWORDS =
      java.util.List.of(
          // 英文：令牌 / 授权（签名类用更精确的 "invalid signature" / "signature invalid"，避免误判
          // 业务文案里的普通 "signature" 用法）
          "token",
          "unauthorized",
          "not authorized",
          "invalid signature",
          "signature invalid",
          "no auth",
          "login expired",
          // 中文：令牌 / 授权 / 签名
          "令牌",
          "未授权",
          "授权",
          "签名",
          "登录已失效",
          "登录失效",
          "认证失败");

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
