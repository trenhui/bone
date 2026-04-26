package com.bone.tpa.claim.infrastructure.config;

import com.bone.tpa.claim.infrastructure.messaging.executer.ClaimTransactionCheckListener;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;

//todo 配置MQ
//@Configuration
public class RocketMQConfig {

    private final TransactionMQProducer producer;
    private final DefaultMQPushConsumer consumer;

    public RocketMQConfig(TransactionMQProducer producer, DefaultMQPushConsumer consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }

    @Bean
    public TransactionMQProducer transactionMQProducer(ClaimTransactionCheckListener transactionCheckListener) {
        TransactionMQProducer producer = new TransactionMQProducer("TransactionProducerGroup");
        producer.setNamesrvAddr("localhost:9876");
        producer.setTransactionCheckListener(transactionCheckListener);
        return producer;
    }

    @Bean
    public DefaultMQPushConsumer defaultMQPushConsumer(MessageListenerConcurrently messageListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("ConsumerGroup");
        consumer.setNamesrvAddr("localhost:9876");
        consumer.subscribe("EntityCreatedTopic", "*");
        consumer.subscribe("EntityUpdatedTopic", "*");
        consumer.subscribe("EntityDeletedTopic", "*");
        consumer.registerMessageListener(messageListener);
        return consumer;
    }

    @PostConstruct
    public void init() throws Exception {
        producer.start();
        consumer.start();
        System.out.println("RocketMQ Producer and Consumer started.");
    }
}
