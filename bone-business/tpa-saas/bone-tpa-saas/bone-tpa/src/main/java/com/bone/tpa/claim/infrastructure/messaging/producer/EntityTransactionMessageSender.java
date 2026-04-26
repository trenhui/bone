package com.bone.tpa.claim.infrastructure.messaging.producer;

import com.bone.core.domain.entity.AbstractEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.LocalTransactionExecuter;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

@Component
public class EntityTransactionMessageSender {
    private final TransactionMQProducer producer;
    private final ObjectMapper objectMapper;

    public EntityTransactionMessageSender(TransactionMQProducer producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
    }

    public <T extends AbstractEntity<?,?>> void sendTransactionMessage(String topic, T entity, LocalTransactionExecuter localTransactionExecuter, Object arg) {
        try {
            String messageBody = objectMapper.writeValueAsString(entity);
            Message message = new Message(topic, messageBody.getBytes());
            message.putUserProperty("entityType", entity.getClass().getName());
            TransactionSendResult sendResult = producer.sendMessageInTransaction(message, localTransactionExecuter, arg);
            System.out.printf("Transaction message sent to topic %s: %s%n", topic, sendResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
