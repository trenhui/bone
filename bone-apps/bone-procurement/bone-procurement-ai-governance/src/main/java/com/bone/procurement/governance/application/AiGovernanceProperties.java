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

  /** 漂移判定阈值（owner 通过线）；null 表示未配置，任何漂移判词 fail-closed 到 UNDETERMINED。 */
  private Double driftThreshold = null;

  /** 漂移判定最小样本量；默认 20。 */
  private int driftMinSampleSize = 20;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Double getDriftThreshold() {
    return driftThreshold;
  }

  public void setDriftThreshold(Double driftThreshold) {
    this.driftThreshold = driftThreshold;
  }

  public int getDriftMinSampleSize() {
    return driftMinSampleSize;
  }

  public void setDriftMinSampleSize(int driftMinSampleSize) {
    this.driftMinSampleSize = driftMinSampleSize;
  }
}
