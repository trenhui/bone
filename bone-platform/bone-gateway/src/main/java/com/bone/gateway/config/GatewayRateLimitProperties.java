package com.bone.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 网关限流配置。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bone.gateway.ratelimit")
public class GatewayRateLimitProperties {

  /** 是否启用限流（无 Redis 时可关闭）。 */
  private boolean enabled = false;

  /** 窗口内最大请求数。 */
  private long capacity = 100;

  /** 窗口大小（秒）。 */
  private long windowSeconds = 60;

  /** 是否开启用户维度（需要鉴权后 X-User-Id）。 */
  private boolean byUser = true;

  /** 是否开启 IP 维度。 */
  private boolean byIp = true;

  /**
   * 是否开启租户维度（需要鉴权后网关注入的 X-Tenant-Id）。
   *
   * <p><b>防 noisy-neighbor</b>：关闭 byIp/byUser、仅保留本维度时，同一租户的全部流量共享一个配额桶（{@code
   * path:tenant=X}），从而限制单个租户对网关的总体占用，避免大租户拖垮全体。默认关闭，保持既有限流行为不变。
   */
  private boolean byTenant = false;
}
