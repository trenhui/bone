package com.bone.integration.domain.connector;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import com.bone.integration.domain.model.connector.event.ConnectorTestedEvent;
import com.bone.integration.domain.model.connector.valueobject.ConnectorStatus;
import com.bone.integration.domain.model.connector.valueobject.ConnectorType;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_connector")
public class Connector extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String name;
  private ConnectorType type;
  private Map<String, Object> config;
  private ConnectorStatus status;

  public static Connector create(
      Long id, String name, ConnectorType type, Map<String, Object> config) {
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
    connector.id = id;
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

  public void update(String name, ConnectorType type, Map<String, Object> config) {
    if (name == null || name.isBlank()) {
      throw new DomainException("连接器名称不能为空");
    }
    if (type == null) {
      throw new DomainException("连接器类型不能为空");
    }
    this.name = name;
    this.type = type;
    updateConfig(config);
  }

  public void updateConfig(Map<String, Object> config) {
    if (config == null) {
      throw new DomainException("连接器配置不能为空");
    }
    this.config = config;
  }

  public void recordTestResult(boolean success, String message) {
    addDomainEvent(new ConnectorTestedEvent(this.id, success, message));
  }
}
