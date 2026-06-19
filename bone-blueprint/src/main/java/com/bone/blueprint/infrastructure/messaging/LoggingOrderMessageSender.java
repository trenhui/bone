package com.bone.blueprint.infrastructure.messaging;

import com.bone.blueprint.application.integration.port.OrderMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "bone.blueprint.outbox.mq-enabled",
    havingValue = "false",
    matchIfMissing = true)
@Slf4j
public class LoggingOrderMessageSender implements OrderMessageSender {

  @Override
  public void send(String topic, String partitionKey, String envelopeJson) {
    log.info(
        "blueprint-outbox-relay topic={} partitionKey={} envelope={}",
        topic,
        partitionKey,
        envelopeJson);
  }
}
