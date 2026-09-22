package com.bone.system.domain.model.config;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.config.event.ConfigChangedEvent;
import com.bone.system.domain.model.config.event.ConfigCreatedEvent;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.model.config.vo.ConfigType;
import com.bone.system.domain.model.config.vo.ConfigValue;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_config")
public class SystemConfig extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

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
      boolean encrypted) {
    SystemConfig config = new SystemConfig();
    config.id = id;
    config.configKey = configKey;
    config.configValue = configValue;
    config.description = description;
    config.configType = configType;
    config.encrypted = encrypted;
    config.createdAt = LocalDateTime.now();
    config.updatedAt = LocalDateTime.now();
    config.addDomainEvent(new ConfigCreatedEvent(config));
    return config;
  }

  public void updateValue(ConfigValue newValue, String operator) {
    ConfigValue oldValue = this.configValue;
    this.configValue = newValue;
    this.updatedAt = LocalDateTime.now();
    addDomainEvent(
        new ConfigChangedEvent(
            this.id, configKey.value(), oldValue.value(), newValue.value(), operator));
  }

  public void updateDescription(String description) {
    this.description = description;
    this.updatedAt = LocalDateTime.now();
  }
}
