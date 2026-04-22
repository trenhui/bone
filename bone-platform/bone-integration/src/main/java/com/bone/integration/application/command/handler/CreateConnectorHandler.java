package com.bone.integration.application.command.handler;

import com.bone.integration.application.command.cmd.CreateConnectorCmd;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateConnectorHandler {
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;

    @Transactional
    public Long handle(CreateConnectorCmd cmd) {
        connectorService.validateConnectorName(cmd.name(), null);
        ConnectorType type = ConnectorType.fromString(cmd.type());
        Connector connector = Connector.create(cmd.name(), type, cmd.config());
        connectorRepository.save(connector);
        return connector.getId().value();
    }
}