package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MQ 连接器：未实现前显式失败（INT-04）。
 */
@Component("MQ")
public class MqClientImpl implements ExternalSystemClient {

    private static final String DETAIL = "连接器尚未实现";

    @Override
    public boolean testConnection(Map<String, Object> config) {
        String brokerUrl = (String) config.get("brokerUrl");
        if (brokerUrl == null || brokerUrl.isBlank()) {
            return false;
        }
        throw ConnectorClientSupport.notImplemented("MQ", DETAIL);
    }

    @Override
    public Object sendRequest(String endpoint, Map<String, Object> params, Map<String, Object> config) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("队列名称不能为空");
        }
        throw ConnectorClientSupport.notImplemented("MQ", DETAIL);
    }

    @Override
    public String getType() {
        return "MQ";
    }
}
