package com.bone.tpa.push.config;

import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.Producer;
import com.aliyun.openservices.ons.api.PropertyKeyConst;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@ConfigurationProperties(prefix = "aliyun.rocketmq")
@Data
public class RocketMQConfig {

    private String accessKey;

    private String secretKey;

    private String nameServer;

    private String sendMsgTimeoutMillis="3000";

    private String topic = "pk-urgentClaim-push";
    private String yctag = "yccbx_tpa";

    /**
     * 顺序消息生产者
     */
    @Bean(destroyMethod = "shutdown")
    public Producer pushFrozonProducer() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.AccessKey, accessKey);
        properties.setProperty(PropertyKeyConst.SecretKey, secretKey);
        properties.setProperty(PropertyKeyConst.NAMESRV_ADDR, nameServer);
        properties.setProperty(PropertyKeyConst.SendMsgTimeoutMillis, sendMsgTimeoutMillis);

        Producer producer = ONSFactory.createProducer(properties);
        producer.start();
        return producer;
    }
}
