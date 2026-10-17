package com.bone.aiintegration.domain.gateway;

import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;

/** 集成调用审计端口（消费方可接 DB / 消息，默认无操作）。 */
public interface IntegrationAuditPort {

  /** 记录一次外部调用的入参与结果（审计三件套：谁调了谁、三态裁定、成功/失败）。 */
  void record(ExternalCall call, ExternalCallOutcome outcome);
}
