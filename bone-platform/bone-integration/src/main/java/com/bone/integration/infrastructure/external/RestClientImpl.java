package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component("REST")
public class RestClientImpl implements ExternalSystemClient {
    @Override
    public boolean testConnection(Map<String, Object> config) {
        // 测试REST连接
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            return false;
        }
        // TODO: 实现连接测试
        return true;
    }

    @Override
    public Object sendRequest(String endpoint, Map<String, Object> params, Map<String, Object> config) {
        // 发送REST请求
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            throw new RuntimeException("URL不能为空");
        }
        // TODO: 实现HTTP请求发送
        return Map.of("status", "success", "data", params);
    }

    @Override
    public String getType() {
        return "REST";
    }
}