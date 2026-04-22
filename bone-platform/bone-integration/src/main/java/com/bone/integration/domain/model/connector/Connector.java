package com.bone.integration.domain.model.connector;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import com.bone.integration.domain.model.connector.vo.ConnectorId;
import com.bone.integration.domain.model.connector.vo.ConnectorStatus;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Connector extends AggregateRoot<Long> {
    private ConnectorId id;
    private String name;
    private ConnectorType type;
    private Map<String, Object> config;
    private ConnectorStatus status;

    public static Connector create(String name, ConnectorType type, Map<String, Object> config) {
        if (name == null || name.isBlank()) {
            throw new DomainException("连接器名称不能为空");
        }
        if (type == null) {
            throw new DomainException("连接器类型不能为空");
        }
        if (config == null) {
            throw new DomainException("连接器配置不能为空");
        }

        Connector connector = new Connector();
        connector.name = name;
        connector.type = type;
        connector.config = config;
        connector.status = ConnectorStatus.DISABLED;
        connector.addDomainEvent(new ConnectorCreatedEvent(connector));
        return connector;
    }

    public void enable() {
        if (this.status == ConnectorStatus.ENABLED) {
            throw new DomainException("连接器已启用");
        }
        this.status = ConnectorStatus.ENABLED;
    }

    public void disable() {
        if (this.status == ConnectorStatus.DISABLED) {
            throw new DomainException("连接器已禁用");
        }
        this.status = ConnectorStatus.DISABLED;
    }

    public void updateConfig(Map<String, Object> config) {
        if (config == null) {
            throw new DomainException("连接器配置不能为空");
        }
        this.config = config;
    }

    void setId(Long id) {
        this.id = ConnectorId.of(id);
    }
}