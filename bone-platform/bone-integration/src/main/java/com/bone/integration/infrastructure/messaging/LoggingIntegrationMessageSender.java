package com.bone.integration.infrastructure.messaging;

import com.bone.integration.application.event.port.IntegrationMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bone.integration.outbox.mq-enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingIntegrationMessageSender implements IntegrationMessageSender {

    @Override
    public void send(String topic, String partitionKey, String envelopeJson) {
        log.info("integration-outbox-relay topic={} partitionKey={} envelope={}", topic, partitionKey, envelopeJson);
    }
}
