package com.bone.aiintegration.application;

import com.bone.aiintegration.domain.gateway.ExternalSystemPort;
import com.bone.aiintegration.domain.gateway.IntegrationAuditPort;
import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;
import com.bone.aiintegration.domain.model.ExternalCallResult;
import com.bone.aiintegration.domain.service.IntegrationPolicy;

/** 集成节点服务（三态）：委派端口执行外部调用，再由 IntegrationPolicy 裁定 used/shadow 语义；落审计。 */
public class IntegrationApplicationService {

  private final ExternalSystemPort externalSystemPort;
  private final IntegrationAuditPort auditPort;

  public IntegrationApplicationService(
      ExternalSystemPort externalSystemPort, IntegrationAuditPort auditPort) {
    this.externalSystemPort = externalSystemPort;
    this.auditPort = auditPort;
  }

  public ExternalCallResult invoke(ExternalCall call) {
    ExternalCallOutcome outcome = externalSystemPort.call(call);
    auditPort.record(call, outcome);
    return IntegrationPolicy.apply(call, outcome);
  }
}
