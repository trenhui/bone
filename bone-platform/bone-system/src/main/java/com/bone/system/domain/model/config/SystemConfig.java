package com.bone.system.domain.model.config;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.config.event.ConfigChangedEvent;
import com.bone.system.domain.model.config.event.ConfigCreatedEvent;
import com.bone.system.domain.model.config.valueobject.ConfigKey;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import com.bone.system.domain.model.config.valueobject.ConfigValue;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 系统配置聚合。
 *
 * <p><b>租户语义（重要）</b>：本表 DDL 有 {@code tenant_id} 且唯一键是 {@code uk_sys_config_key (tenant_id,
 * config_key)}——即设计口径<b>已确认为 per-tenant</b>。因此实体必须继承 {@link TenantAggregateRoot}：SDK 的 {@code
 * TableMetadata.isTenantScoped()} 只看<b>实体是否声明 tenantId </b>，不声明则 {@code TenantFilterInjector} 直接
 * return，查询静默不带租户条件， 表现为「A 租户能读到 B 租户的配置」且「相同 configKey 跨租户互相覆盖」。
 *
 * @see com.bone.system.domain.model.dict.SysDictType 同模块已完成租户声明的参照样板
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_config")
public class SystemConfig extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @com.bone.metadata.sdk.domain.annotation.Version private Long version;

  private ConfigKey configKey;
  private ConfigValue configValue;
  private String description;
  private ConfigType configType;
  private boolean encrypted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SystemConfig create(
      Long id,
      ConfigKey configKey,
      ConfigValue configValue,
      String description,
      ConfigType configType,
      boolean encrypted,
      String operator,
      Long tenantId) {
    SystemConfig config = new SystemConfig();
    config.id = id;
    config.configKey = configKey;
    config.configValue = configValue;
    config.description = description;
    config.configType = configType;
    config.encrypted = encrypted;
    config.setTenantId(tenantId);
    config.createdAt = LocalDateTime.now();
    config.updatedAt = LocalDateTime.now();
    config.addDomainEvent(new ConfigCreatedEvent(config, operator, tenantId));
    return config;
  }

  public void updateValue(ConfigValue newValue, String operator, Long tenantId) {
    ConfigValue oldValue = this.configValue;
    this.configValue = newValue;
    this.updatedAt = LocalDateTime.now();
    addDomainEvent(
        new ConfigChangedEvent(
            this.id, configKey.value(), oldValue.value(), newValue.value(), operator, tenantId));
  }

  public void updateDescription(String description) {
    this.description = description;
    this.updatedAt = LocalDateTime.now();
  }
}
