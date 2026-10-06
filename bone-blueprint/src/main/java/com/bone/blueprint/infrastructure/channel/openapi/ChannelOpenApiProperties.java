package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 渠道开放平台接入配置。
 *
 * <p><b>{@code transport} 为什么默认 {@code MOCK}</b>：本地开发与 CI 没有渠道凭证，而渠道侧一旦配置错误会直接造成 真实商品/真实订单写坏。默认
 * MOCK 让「不配置凭证」=「只跑归一化逻辑」； 但<strong>默认 MOCK 不等于静默成功</strong>： 切到 {@code HTTP} 后凭证缺失会立即抛 {@code
 * BP_CHANNEL_CREDENTIAL_MISSING}，绝不悄悄退回落 MOCK（否则生产上「以为在同步真实渠道， 实际一直在跑模拟」是最危险的一类静默）。
 *
 * @see ChannelTransport
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bone.blueprint.channel.openapi")
public class ChannelOpenApiProperties {

  /** 传输通道：MOCK（本地/CI，只跑归一化）或 HTTP（真实开放平台）。 */
  private Transport transport = Transport.MOCK;

  /** 连接超时（毫秒）。 */
  private int connectTimeoutMs = 3000;

  /** 读超时（毫秒）：渠道普遍比内部接口慢，太短会大面积超时，太长会占满连接池。 */
  private int readTimeoutMs = 10000;

  /** 单次调用的最大尝试次数（仅对网络类错误重试；渠道业务拒绝不重试）。 */
  private int maxAttempts = 2;

  /** 各渠道凭证；键为渠道码（TAOBAO / JD / DOUYIN / PDD）。 */
  private Map<String, ChannelCredentialConfig> credentials = new HashMap<>();

  /** 渠道开放平台传输通道。 */
  public enum Transport {
    /** 走本地归一化（不出发 HTTP，扩展实现回落上下文兜底）。 */
    MOCK,
    /** 走真实开放平台网关。 */
    HTTP
  }
}
