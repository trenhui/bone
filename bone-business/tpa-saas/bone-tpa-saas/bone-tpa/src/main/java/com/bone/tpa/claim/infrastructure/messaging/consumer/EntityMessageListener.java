package com.bone.tpa.claim.infrastructure.messaging.consumer;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.tpa.claim.infrastructure.messaging.event.ClaimEventHandler;
import com.bone.tpa.sdk.claim.model.Claim;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EntityMessageListener implements MessageListenerConcurrently {
    private final ClaimEventHandler claimEventHandler;
    private final ObjectMapper objectMapper;

    public EntityMessageListener(ClaimEventHandler claimEventHandler) {
        this.claimEventHandler = claimEventHandler;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            String topic = msg.getTopic();
            String messageBody = new String(msg.getBody());
            String entityType = msg.getProperty("entityType");

            if (entityType == null) {
                throw new IllegalArgumentException("Message does not contain entityType property");
            }

            // 根据主题和实体类型进行转换和处理
            switch (topic) {
                case "EntityCreatedTopic":
                    Claim createdEntity = (Claim) convertMessageToEntity(messageBody, entityType);
                    claimEventHandler.handleEvent(createdEntity);
                    break;
                case "EntityUpdatedTopic":
                    Claim updatedEntity = (Claim) convertMessageToEntity(messageBody, entityType);
                    claimEventHandler.handleEvent(updatedEntity);
                    break;
                case "EntityDeletedTopic":
                    Claim deletedEntity = (Claim) convertMessageToEntity(messageBody, entityType);
                    claimEventHandler.handleEvent(deletedEntity);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown topic: " + topic);
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    // 泛型方法用于将消息体转换为实体
    private AbstractEntity<?,?> convertMessageToEntity(String messageBody, String entityType) {
        try {
            Class<?> clazz = Class.forName(entityType);
            return (AbstractEntity<?,?>) objectMapper.readValue(messageBody, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert message to entity", e);
        }
    }
}
