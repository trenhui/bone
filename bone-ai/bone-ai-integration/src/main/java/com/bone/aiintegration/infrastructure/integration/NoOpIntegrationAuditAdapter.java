package com.bone.aiintegration.infrastructure.integration;

import com.bone.aiintegration.domain.gateway.IntegrationAuditPort;
import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认无操作审计（不落库）；接入方可提供 {@code @Bean IntegrationAuditPort} 覆盖以落 DB。 */
public class NoOpIntegrationAuditAdapter implements IntegrationAuditPort {

  private static final Logger log = LoggerFactory.getLogger(NoOpIntegrationAuditAdapter.class);

  @Override
  public void record(ExternalCall call, ExternalCallOutcome outcome) {
    log.debug(
        "集成调用审计(无操作): callId={} systemId={} status={}",
        call.callId(),
        call.systemId(),
        outcome.httpStatus());
  }
}
