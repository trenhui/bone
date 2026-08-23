package com.bone.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 网关熔断开关。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bone.gateway.resilience")
public class GatewayResilienceProperties {

  /** 是否启用熔断。 */
  private boolean enabled = true;
}
