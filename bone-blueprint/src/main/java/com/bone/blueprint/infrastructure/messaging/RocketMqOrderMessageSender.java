package com.bone.blueprint.infrastructure.messaging;

import com.bone.blueprint.domain.gateway.OrderMessageSender;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bone.blueprint.outbox.mq-enabled", havingValue = "true")
@RequiredArgsConstructor
public class RocketMqOrderMessageSender implements OrderMessageSender {

  private final RocketMQTemplate rocketMQTemplate;

  @Override
  public void send(String topic, String partitionKey, String envelopeJson) {
    rocketMQTemplate.syncSend(
        topic, MessageBuilder.withPayload(envelopeJson).setHeader("KEYS", partitionKey).build());
  }
}
