package com.bone.integration.domain.model.connector;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
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
public class Connector extends TenantAggregateRoot<Long> {
  @Id

  /**
   * 逻辑删除标记。
   *
   * <p><b>为何必须显式声明</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是 <b>实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 的字段</b>，与 DDL 有无 {@code deleted} 列无关。 {@code
   * TenantAggregateRoot} 只提供 {@code tenantId}，不像 {@code AbstractEntity} 那样自带该字段； 本类若不声明，{@code
   * Repository#deleteById} 会发出 {@code DELETE FROM} —— 删除即整行物理消失、不可审计不可恢复。
   *
   * <p>本表<b>无唯一索引</b>，故恢复软删不存在「同值无法重建」冲突（见soft-delete-declaration-baseline.json 的 {@code
   * _uk_conflict} 段）。
   */
  @Deleted
  private Boolean deleted = false;

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
