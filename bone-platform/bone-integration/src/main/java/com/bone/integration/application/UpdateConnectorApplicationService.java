package com.bone.integration.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.integration.application.command.cmd.UpdateConnectorCommand;
import com.bone.integration.application.support.ConnectorSecretSupport;
import com.bone.integration.application.support.ConnectorSupport;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
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
  private final ConnectorSecretSupport connectorSecretSupport;

  @Transactional
  public void handle(UpdateConnectorCommand cmd) {
    Connector connector = connectorRepository.findById(cmd.id());
    if (connector == null) {
      throw IntegrationErrors.of(IntegrationErrorCodes.CONNECTOR_NOT_FOUND, cmd.id());
    }
    connectorSupport.validateConnectorName(cmd.name(), cmd.id());
    ConnectorType type = ConnectorType.fromString(cmd.type());
    // 合并而非覆盖：读端已不返回凭据，前端回填的 config 里必然没有 secretKey 这类键，
    // 直接覆盖会让「一次无关字段的编辑」把凭据清空、连接器当场失效。
    connector.update(
        cmd.name(),
        type,
        connectorSecretSupport.mergeOnUpdate(cmd.config(), connector.getConfig()));
    connectorRepository.save(connector);
  }
}
