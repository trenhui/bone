package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道开放平台调用请求。
 *
 * <p><b>为何是「平台中立」的入参，而不是让扩展实现直接拼完整 HTTP 请求</b>：拼完整请求意味着每个渠道实现都要自己处理 网关地址、公共参数、签名、 超时、重试， 12 份实现就会出现
 * 12 套签名实现——签名写错是最难定位的错误（渠道侧只回一句 {@code sign error}，且本地永远测不出来， 因为本地没有渠道校验）。
 * 本类只表达<strong>「调哪个渠道的哪个接口、带什么业务参数」</strong>，协议细节全部由上{@link ChannelApiSpec} 与 {@link
 * ChannelOpenApiClient} 统一处理。
 *
 * @param channelCode 渠道码（TAOBAO / JD / DOUYIN / PDD）
 * @param apiMethod 平台侧接口名（如 {@code taobao.trade.orders.get}）
 * @param params 业务参数（渠道公共参数与签名由客户端补全）
 * @param tenantId 租户ID（写凭证缓存键与日志，业务上不参与请求）
 */
public record ChannelApiRequest(
    String channelCode, String apiMethod, Map<String, String> params, Long tenantId) {

  /** 业务参数容器：保持插入序，便于日志里的参数顺序稳定（排障时肉眼可比对）。 */
  public static ChannelApiRequest of(String channelCode, String apiMethod, Long tenantId) {
    return new ChannelApiRequest(channelCode, apiMethod, new LinkedHashMap<>(), tenantId);
  }

  public ChannelApiRequest with(String key, String value) {
    Map<String, String> copy = new LinkedHashMap<>(params);
    if (key == null || value == null) {
      return new ChannelApiRequest(channelCode, apiMethod, copy, tenantId);
    }
    copy.put(key, value);
    return new ChannelApiRequest(channelCode, apiMethod, copy, tenantId);
  }

  /** 批量补参；{@code null} 值的条目直接跳过（渠道普遍用「不传」表示「不查该字段」）。 */
  public ChannelApiRequest withAll(Map<String, String> extra) {
    if (extra == null || extra.isEmpty()) {
      return this;
    }
    Map<String, String> copy = new LinkedHashMap<>(params);
    extra.forEach(
        (k, v) -> {
          if (k != null && v != null) {
            copy.put(k, v);
          }
        });
    return new ChannelApiRequest(channelCode, apiMethod, copy, tenantId);
  }
}
