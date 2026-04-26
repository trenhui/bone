package com.bone.tpa.claim.infrastructure.messaging.executer;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionCheckListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClaimTransactionCheckListener implements TransactionCheckListener {
    @Override
    public LocalTransactionState checkLocalTransactionState(MessageExt msg) {
        // 检查本地事务状态
        System.out.println("Checking local transaction for message: " + new String(msg.getBody()));
        // 这里应该检查本地事务状态并返回相应的事务状态
        return LocalTransactionState.COMMIT_MESSAGE;
    }

    @Bean
    public TransactionMQProducer transactionMQProducer() {
        TransactionMQProducer producer = new TransactionMQProducer("TransactionProducerGroup");
        producer.setNamesrvAddr("localhost:9876");
        producer.setTransactionCheckListener(this);
        return producer;
    }
}
