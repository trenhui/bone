package com.bone.procurement.advisory.domain.model.advisory;

import java.util.Arrays;
import java.util.Objects;

/**
 * 业务 AI 能力码（D3-v2 §1.1 八码登记口径）。本骨架只登记码与场景，不接模型、不声明效果达标。
 *
 * <p>能力码与治理模块 CapabilityCode 的字符串取值对齐：治理决定「能否进入推理路径」，本模块决定「以何种建议态出域」。
 */
public enum AdvisoryCapability {
  M1("招标体检", "tender-health-check"),
  M2("风险审查", "risk-review"),
  M3("供应商准入辅助", "supplier-onboarding-assist"),
  M4("辅助评标", "bid-evaluation-assist"),
  M5("智能问答", "knowledge-qa"),
  M6("价格分析", "price-analysis"),
  M7("合同起草", "contract-drafting"),
  ORCHESTRATION("编排路由", "orchestration");

  private final String displayName;
  private final String codeValue;

  AdvisoryCapability(String displayName, String codeValue) {
    this.displayName = displayName;
    this.codeValue = codeValue;
  }

  public String displayName() {
    return displayName;
  }

  public String codeValue() {
    return codeValue;
  }

  public static AdvisoryCapability parse(String raw) {
    if (raw == null) return null;
    return Arrays.stream(values())
        .filter(
            c -> c.name().equalsIgnoreCase(raw.trim()) || c.codeValue.equalsIgnoreCase(raw.trim()))
        .findFirst()
        .orElse(null);
  }

  public String governanceCode() {
    return Objects.toString(codeValue);
  }
}
