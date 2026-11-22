package com.bone.procurement.readiness;

import java.util.List;

/** 就绪度只读视图。deploymentGatesPassed 与 authorizationNotice 显式分离"部署门禁通过"与 "授标/放款许可"，前端不得把前者渲染为后者。 */
public record AiReadinessView(
    AiReadinessVerdict verdict,
    String verdictText,
    boolean providerConfigured,
    boolean governanceEnabled,
    boolean killSwitchEngaged,
    boolean evalFrozen,
    boolean inferenceCapabilityRegistered,
    boolean deploymentGatesPassed,
    String authorizationNotice,
    List<String> reasons) {

  public static final String DEFAULT_NOTICE = "部署门禁与就绪度只表示工程护栏通过，不等于授标、准入或放款许可；采购最终决定由人工作出。";

  public static AiReadinessView unknown(String reason) {
    return new AiReadinessView(
        AiReadinessVerdict.UNKNOWN,
        "UNKNOWN",
        false,
        false,
        false,
        false,
        false,
        false,
        DEFAULT_NOTICE,
        List.of(reason));
  }
}
