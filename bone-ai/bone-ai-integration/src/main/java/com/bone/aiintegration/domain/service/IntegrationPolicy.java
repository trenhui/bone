package com.bone.aiintegration.domain.service;

import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;
import com.bone.aiintegration.domain.model.ExternalCallResult;
import com.bone.aiintegration.domain.model.IntegrationMode;

/** 三态裁定（领域规则）：AUTOMATED→结果用于流程；SHADOW→仅观测不落地；ASSISTED→待人工确认。 */
public final class IntegrationPolicy {

  private IntegrationPolicy() {}

  public static ExternalCallResult apply(ExternalCall call, ExternalCallOutcome outcome) {
    boolean used = call.mode() == IntegrationMode.AUTOMATED;
    boolean shadow = call.mode() == IntegrationMode.SHADOW;
    return new ExternalCallResult(
        call.callId(),
        call.systemId(),
        outcome.httpStatus(),
        outcome.body(),
        call.mode(),
        used,
        shadow,
        outcome.latencyMs(),
        outcome.error());
  }
}
