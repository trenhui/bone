package com.bone.procurement.contract.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 合同 AI 审查能力开关属性。
 *
 * <p>默认关闭（fail-closed）：只有显式配置 {@code bone.procurement.contract-ai-review.enabled=true} 才装配审查 bean /
 * 注册端点。属性缺失、值为 false 或其他值一律视为关闭。
 */
@ConfigurationProperties(prefix = ContractAiReviewProperties.PREFIX)
public class ContractAiReviewProperties {

  public static final String PREFIX = "bone.procurement.contract-ai-review";
  public static final String ENABLED_KEY = PREFIX + ".enabled";

  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
