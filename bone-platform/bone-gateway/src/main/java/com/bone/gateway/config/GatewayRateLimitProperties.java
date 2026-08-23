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

  /** 限流维度：ip / user / both。 */
  private String limitBy = "both";

  /** 是否开启用户维度（需要鉴权后 X-User-Id）。 */
  private boolean byUser = true;

  /** 是否开启 IP 维度。 */
  private boolean byIp = true;
}
