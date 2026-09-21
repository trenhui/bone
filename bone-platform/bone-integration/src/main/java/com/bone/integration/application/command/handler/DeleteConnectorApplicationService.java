package com.bone.integration.application.command.handler;

import com.bone.integration.application.command.cmd.DeleteConnectorCommand;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 删除连接器命令处理器 */
@Component
@RequiredArgsConstructor
public class DeleteConnectorApplicationService {

  private final ConnectorRepository connectorRepository;

  @Transactional
  public void handle(DeleteConnectorCommand cmd) {
    connectorRepository.deleteById(cmd.id());
  }
}
