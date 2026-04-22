package com.bone.integration.application.command.handler;

import com.bone.integration.application.command.cmd.UpdateConnectorCmd;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        connector.updateConfig(cmd.config());
        connectorRepository.save(connector);
    }
}