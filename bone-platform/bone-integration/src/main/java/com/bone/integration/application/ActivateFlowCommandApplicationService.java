package com.bone.integration.application;

import com.bone.integration.application.command.ActivateFlowCommand;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 激活流程命令处理器 */
@Component
@RequiredArgsConstructor
public class ActivateFlowCommandApplicationService {
  private final IntegrationFlowRepository flowRepository;
  private final IntegrationDomainEventPublisher domainEventPublisher;

  @Transactional
  public void handle(ActivateFlowCommand cmd) {
    IntegrationFlow flow = flowRepository.findById(cmd.id());
    if (flow == null) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_NOT_FOUND, cmd.id());
    }
    flow.activate();
    flowRepository.save(flow);
    domainEventPublisher.publishFrom(flow);
  }
}
