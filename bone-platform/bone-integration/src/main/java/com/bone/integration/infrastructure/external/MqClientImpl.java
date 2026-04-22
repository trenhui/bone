package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("MQ")
public class MqClientImpl implements ExternalSystemClient {
    @Override
    public boolean testConnection(Map<String, Object> config) {
        // 测试MQ连接
        String brokerUrl = (String) config.get("brokerUrl");
        if (brokerUrl == null || brokerUrl.isBlank()) {
            return false;
        }
        // TODO: 实现连接测试
        return true;
    }

    @Override
    public Object sendRequest(String endpoint, Map<String, Object> params, Map<String, Object> config) {
        // 发送消息到MQ
        String queueName = endpoint;
        if (queueName == null || queueName.isBlank()) {
            throw new RuntimeException("队列名称不能为空");
        }
        // TODO: 实现消息发送
        return Map.of("status", "success", "queue", queueName, "message", params);
    }

    @Override
    public String getType() {
        return "MQ";
    }
}