package com.bone.aiintegration.application;

import com.bone.aiintegration.domain.gateway.ExternalSystemPort;
import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallResult;
import com.bone.aiintegration.domain.service.IntegrationPolicy;

/** 集成节点服务（三态）：委派端口执行外部调用，再由 IntegrationPolicy 裁定 used/shadow 语义。 */
public class IntegrationService {

  private final ExternalSystemPort externalSystemPort;

  public IntegrationService(ExternalSystemPort externalSystemPort) {
    this.externalSystemPort = externalSystemPort;
  }

  public ExternalCallResult invoke(ExternalCall call) {
    return IntegrationPolicy.apply(call, externalSystemPort.call(call));
  }
}
