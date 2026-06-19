package com.bone.integration.application.command.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.EnableConnectorCommand;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 启用连接器命令处理器 */
@Component
@RequiredArgsConstructor
public class EnableConnectorHandler {

  private final ConnectorRepository connectorRepository;

  @Transactional
  public void handle(EnableConnectorCommand cmd) {
    Connector connector =
        QueryBuilder.from(Connector.class)
            .where(Connector::getId)
            .eq(cmd.id())
            .first()
            .orElse(null);
    if (connector == null) {
      throw new DomainException("连接器不存在");
    }
    connector.enable();
    connectorRepository.save(connector);
  }
}
