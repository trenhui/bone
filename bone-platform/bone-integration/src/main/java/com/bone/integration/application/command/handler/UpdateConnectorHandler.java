package com.bone.integration.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.integration.application.command.cmd.UpdateConnectorCmd;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.connector.vo.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateConnector",
    description = "更新集成连接器配置",
    inputSchema = "{\"id\": \"long\", \"name\": \"string\", \"type\": \"string\", \"config\": \"object\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class UpdateConnectorHandler {
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;

    @Transactional
    public void handle(UpdateConnectorCmd cmd) {
        Connector connector = connectorRepository.findById(cmd.id())
                .orElseThrow(() -> new com.bone.core.exception.DomainException("连接器不存在"));
        connectorService.validateConnectorName(cmd.name(), cmd.id());
        ConnectorType type = ConnectorType.fromString(cmd.type());
        connector.update(cmd.name(), type, cmd.config());
        connectorRepository.save(connector);
    }
}
