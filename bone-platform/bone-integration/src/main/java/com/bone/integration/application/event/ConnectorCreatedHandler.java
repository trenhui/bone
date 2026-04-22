package com.bone.integration.application.event;

import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConnectorCreatedHandler {
    public void handle(ConnectorCreatedEvent event) {
        log.info("Connector created: id={}, name={}, type={}", event.connectorId(), event.name(), event.type());
        // TODO: 发送通知或执行其他逻辑
    }
}