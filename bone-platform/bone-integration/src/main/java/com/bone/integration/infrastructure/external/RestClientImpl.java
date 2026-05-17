package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * REST 连接器：未实现前显式失败，禁止假成功（见 doc/wiki/07-P0-TODO INT-01）。
 */
@Component("REST")
public class RestClientImpl implements ExternalSystemClient {

    private static final String NOT_IMPLEMENTED =
            "REST 连接器尚未实现，请等待 INT-01 完成或改用已支持的连接器类型";

    @Override
    public boolean testConnection(Map<String, Object> config) {
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            return false;
        }
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Object sendRequest(String endpoint, Map<String, Object> params, Map<String, Object> config) {
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL不能为空");
        }
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public String getType() {
        return "REST";
    }
}
