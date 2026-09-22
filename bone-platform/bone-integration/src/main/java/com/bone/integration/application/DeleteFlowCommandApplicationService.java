package com.bone.integration.application;

import com.bone.integration.application.command.cmd.DeleteFlowCommand;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 删除流程命令处理器 */
@Component
@RequiredArgsConstructor
public class DeleteFlowCommandApplicationService {
  private final IntegrationFlowRepository flowRepository;

  @Transactional
  public void handle(DeleteFlowCommand cmd) {
    flowRepository.deleteById(cmd.id());
  }
}
