package com.bone.integration.application;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.DeactivateFlowCommand;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 停用流程命令处理器 */
@Component
@RequiredArgsConstructor
public class DeactivateFlowCommandApplicationService {
  private final IntegrationFlowRepository flowRepository;

  @Transactional
  public void handle(DeactivateFlowCommand cmd) {
    IntegrationFlow flow = flowRepository.findById(cmd.id());
    if (flow == null) {
      throw new DomainException("流程不存在");
    }
    flow.deactivate();
    flowRepository.save(flow);
  }
}
