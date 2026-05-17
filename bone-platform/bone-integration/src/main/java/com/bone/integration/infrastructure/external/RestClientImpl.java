package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * REST 连接器：未实现前显式失败，禁止假成功（见 doc/wiki/07-P0-TODO INT-01）。
 */
@Component("REST")
public class RestClientImpl implements ExternalSystemClient {

    private static final String DETAIL = "连接器尚未实现";

    @Override
    public boolean testConnection(Map<String, Object> config) {
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            return false;
        }
        throw ConnectorClientSupport.notImplemented("REST", DETAIL);
    }

    @Override
    public Object sendRequest(String endpoint, Map<String, Object> params, Map<String, Object> config) {
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL不能为空");
        }
        throw ConnectorClientSupport.notImplemented("REST", DETAIL);
    }

    @Override
    public String getType() {
        return "REST";
    }
}
