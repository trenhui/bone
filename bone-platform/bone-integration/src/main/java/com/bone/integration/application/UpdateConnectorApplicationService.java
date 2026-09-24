package com.bone.integration.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.UpdateConnectorCommand;
import com.bone.integration.application.support.ConnectorSupport;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.connector.valueobject.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateConnector",
    description = "更新集成连接器配置",
    inputSchema =
        "{\"id\": \"long\", \"name\": \"string\", \"type\": \"string\", \"config\": \"object\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15)
/**
 * 更新集成连接器配置。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：连接器状态迁移由 integration 的 Outbox 在基础设施层发布集成事件， 聚合本身不发布 Bone
 * 领域事件；故按 E-5.4（集成事件由 Outbox 另管）声明豁免。 若将来需聚合级领域事件，须改为调用 publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
public class UpdateConnectorApplicationService {
  private final ConnectorRepository connectorRepository;
  private final ConnectorSupport connectorSupport;

  @Transactional
  public void handle(UpdateConnectorCommand cmd) {
    Connector connector = connectorRepository.findById(cmd.id());
    if (connector == null) {
      throw new DomainException("连接器不存在");
    }
    connectorSupport.validateConnectorName(cmd.name(), cmd.id());
    ConnectorType type = ConnectorType.fromString(cmd.type());
    connector.update(cmd.name(), type, cmd.config());
    connectorRepository.save(connector);
  }
}
