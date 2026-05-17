package com.bone.integration.domain.service;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.client.ExternalSystemClient;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConnectorService {
    private final ConnectorRepository connectorRepository;
    private final Map<String, ExternalSystemClient> externalSystemClients;

    public boolean testConnector(Connector connector) {
        ExternalSystemClient client = externalSystemClients.get(connector.getType().name());
        if (client == null) {
            throw new DomainException("不支持的连接器类型: " + connector.getType());
        }
        return client.testConnection(connector.getConfig());
    }

    public Object executeConnector(Connector connector, String endpoint, Map<String, Object> params) {
        ExternalSystemClient client = externalSystemClients.get(connector.getType().name());
        if (client == null) {
            throw new DomainException("不支持的连接器类型: " + connector.getType());
        }
        return client.sendRequest(endpoint, params, connector.getConfig());
    }

    public void validateConnectorName(String name, Long excludeId) {
        Connector existing =
                connectorRepository.findOneByCriteria(Criteria.<Connector>create().eq("name", name));
        if (existing != null && (excludeId == null || !excludeId.equals(existing.getId()))) {
            throw new DomainException("连接器名称已存在");
        }
    }
}
