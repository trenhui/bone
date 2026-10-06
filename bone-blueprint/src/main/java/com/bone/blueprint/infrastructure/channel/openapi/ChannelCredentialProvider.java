package com.bone.blueprint.infrastructure.channel.openapi;

import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 渠道凭证供给 —— 把「渠道码 → 凭证」这件事收口成一处，供开放平台客户端取用。
 *
 * <p><b>两级来源</b>：① 运行期刷新写回的令牌（内存，进程内优先）；② yml / 配置中心的静态配置。
 * 运行时令牌优先于静态配置，否则「刚刷新过的令牌被重启前的旧值覆盖」这类问题无从发现。
 *
 * <p><b>失败关闭（关键）</b>：{@link #require} 在拿不到凭证时<strong>直接抛业务错</strong>，绝不退回空凭证或悄悄回落 MOCK
 * 通道。端到端联调时最危险的不是「调用了渠道被拒」，而是「生产上以为在同步真实渠道，实际上一直在跑模拟数据」—— 那会让所有 渠道侧反馈（价格不同步、库存超卖）都指向错误方向。
 *
 * <p><b>为什么令牌刷新只更新内存</b>：本模块尚无令牌持久化表（对接真实平台时应补一张 {@code bp_channel_token}，存 {@code access_token /
 * refresh_token / expires_at}）。在补表之前，刷新只影响当前进程，重启回落到静态配置的令牌—— 这是 <strong>可接受的确定性行为</strong>，且被
 * {@link #refresh} 的日志明确暴露，不会假装永久生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelCredentialProvider {

  private final ChannelOpenApiProperties properties;

  /** 运行期刷新写回的令牌：渠道码 → 令牌 + 过期时间。 */
  private final Map<String, ChannelCredentials> runtimeTokens = new ConcurrentHashMap<>();

  /** 取凭证（拿不到即失败）。 */
  public ChannelCredentials require(String channelCode) {
    ChannelCredentials credentials = resolve(channelCode);
    if (credentials == null || !credentials.hasSecret()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_CREDENTIAL_MISSING,
          "渠道 "
              + channelCode
              + " 未配置开放平台凭证（bone.blueprint.channel.openapi.credentials."
              + channelCode
              + "），请先在渠道开放平台申请 appKey/appSecret 后配置");
    }
    return credentials;
  }

  /** 取凭证（允许缺失，返回 {@code null}）。 */
  public ChannelCredentials optional(String channelCode) {
    return resolve(channelCode);
  }

  /**
   * 写入刷新后的令牌（内存态，重启即失效）。
   *
   * @return 是否真的更新了（未配置该渠道时返回 false）
   */
  public boolean refresh(String channelCode, String accessToken) {
    if (channelCode == null || accessToken == null || accessToken.isBlank()) {
      return false;
    }
    ChannelCredentials current = resolve(channelCode);
    if (current == null) {
      return false;
    }
    runtimeTokens.put(
        channelCode,
        new ChannelCredentials(
            current.appKey(), current.appSecret(), accessToken, current.expiresAt()));
    log.warn("[{}] 渠道令牌已在内存刷新（重启后回落到静态配置）: 请接入令牌持久化后再依赖刷新能力", channelCode);
    return true;
  }

  /** 解析顺序：运行期令牌 → 静态配置。 */
  private ChannelCredentials resolve(String channelCode) {
    ChannelCredentials runtime = runtimeTokens.get(channelCode);
    if (runtime != null && runtime.hasToken()) {
      return runtime;
    }
    ChannelCredentialConfig config =
        properties.getCredentials() == null ? null : properties.getCredentials().get(channelCode);
    if (config == null) {
      return runtime;
    }
    return new ChannelCredentials(
        blankToNull(config.getAppKey()),
        blankToNull(config.getAppSecret()),
        blankToNull(config.getAccessToken()),
        parseInstant(config.getExpiresAt()));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception ex) {
      return null;
    }
  }
}
