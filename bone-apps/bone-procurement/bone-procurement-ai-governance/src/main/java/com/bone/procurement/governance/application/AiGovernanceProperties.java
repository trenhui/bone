package com.bone.procurement.governance.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 决策治理能力开关属性（默认关闭 fail-closed）。
 *
 * <p>只有显式配置 {@code bone.procurement.ai-governance.enabled=true} 才装配治理 bean /
 * 注册端点。治理是横切能力，关闭时所有被治理能力的裁决回落到缺省拒绝 （调用方拿不到 governance bean，需自行 fail-closed）。
 */
@ConfigurationProperties(prefix = AiGovernanceProperties.PREFIX)
public class AiGovernanceProperties {

  public static final String PREFIX = "bone.procurement.ai-governance";
  public static final String ENABLED_KEY = PREFIX + ".enabled";

  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
