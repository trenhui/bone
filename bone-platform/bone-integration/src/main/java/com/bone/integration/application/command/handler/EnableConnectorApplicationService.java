package com.bone.integration.application.command.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.EnableConnectorCommand;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 启用连接器命令处理器 */
@Component
@RequiredArgsConstructor
public class EnableConnectorApplicationService {

  private final ConnectorRepository connectorRepository;

  @Transactional
  public void handle(EnableConnectorCommand cmd) {
    Connector connector = connectorRepository.findById(cmd.id());
    if (connector == null) {
      throw new DomainException("连接器不存在");
    }
    connector.enable();
    connectorRepository.save(connector);
  }
}
