package com.bone.integration.application;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.DisableConnectorCommand;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 禁用连接器命令处理器 */
@Component
@RequiredArgsConstructor
public class DisableConnectorApplicationService {

  private final ConnectorRepository connectorRepository;

  @Transactional
  public void handle(DisableConnectorCommand cmd) {
    Connector connector = connectorRepository.findById(cmd.id());
    if (connector == null) {
      throw new DomainException("连接器不存在");
    }
    connector.disable();
    connectorRepository.save(connector);
  }
}
