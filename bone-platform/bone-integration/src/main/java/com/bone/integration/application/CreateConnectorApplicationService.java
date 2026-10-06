package com.bone.integration.application;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.CreateConnectorCommand;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.support.ConnectorSecretSupport;
import com.bone.integration.application.support.ConnectorSupport;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.connector.valueobject.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateConnector",
    description = "创建新的集成连接器",
    inputSchema = "{\"name\": \"string\", \"type\": \"string\", \"config\": \"object\"}",
    outputSchema = "{\"connectorId\": \"long\"}",
    idempotent = false,
    cost = 2,
    retryable = true,
    timeout = 30)
@Component
@RequiredArgsConstructor
public class CreateConnectorApplicationService {
  private final ConnectorRepository connectorRepository;
  private final ConnectorSupport connectorSupport;
  private final ConnectorSecretSupport connectorSecretSupport;
  private final IntegrationDomainEventPublisher domainEventPublisher;

  @Transactional
  public Long handle(CreateConnectorCommand cmd) {
    connectorSupport.validateConnectorName(cmd.name(), null);
    Long connectorId = DistributedIdGenerator.generateLongId();
    ConnectorType type = ConnectorType.fromString(cmd.type());
    // 凭据加密后落库：config 是自由 JSON，第三方密钥原本与 endpoint/bucket 混在一起明文保存，
    // 而读端只需一个读权限就能批量导出全平台凭据。
    Connector connector =
        Connector.create(
            connectorId, cmd.name(), type, connectorSecretSupport.encryptForPersist(cmd.config()));
    connectorRepository.save(connector);
    domainEventPublisher.publishFrom(connector);
    return connector.getId();
  }
}
