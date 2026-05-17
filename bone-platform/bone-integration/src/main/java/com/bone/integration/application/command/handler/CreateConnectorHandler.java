package com.bone.integration.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.cmd.CreateConnectorCmd;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
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
public class CreateConnectorHandler {
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;
    private final IntegrationDomainEventPublisher domainEventPublisher;

    @Transactional
    public Long handle(CreateConnectorCmd cmd) {
        connectorService.validateConnectorName(cmd.name(), null);
        Long connectorId = DistributedIdGenerator.generateLongId();
        ConnectorType type = ConnectorType.fromString(cmd.type());
        Connector connector = Connector.create(connectorId, cmd.name(), type, cmd.config());
        connectorRepository.save(connector);
        domainEventPublisher.publishFrom(connector);
        return connector.getId();
    }
}
