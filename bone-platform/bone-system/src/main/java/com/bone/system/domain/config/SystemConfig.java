package com.bone.system.domain.config;

import com.bone.core.domain.AggregateRoot;
import com.bone.system.domain.config.event.ConfigChangedEvent;
import com.bone.system.domain.config.event.ConfigCreatedEvent;
import com.bone.system.domain.config.vo.ConfigKey;
import com.bone.system.domain.config.vo.ConfigType;
import com.bone.system.domain.config.vo.ConfigValue;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("sys_config")
public class SystemConfig extends AggregateRoot<Long> {
    private Long id;
    private ConfigKey configKey;
    private ConfigValue configValue;
    private String description;
    private ConfigType configType;
    private boolean encrypted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static SystemConfig create(Long id, ConfigKey configKey, ConfigValue configValue,
                                      String description, ConfigType configType, boolean encrypted) {
        SystemConfig config = new SystemConfig();
        config.id = id;
        config.configKey = configKey;
        config.configValue = configValue;
        config.description = description;
        config.configType = configType;
        config.encrypted = encrypted;
        config.createTime = LocalDateTime.now();
        config.updateTime = LocalDateTime.now();
        config.addDomainEvent(new ConfigCreatedEvent(config));
        return config;
    }

    public void updateValue(ConfigValue newValue, String operator) {
        ConfigValue oldValue = this.configValue;
        this.configValue = newValue;
        this.updateTime = LocalDateTime.now();
        addDomainEvent(new ConfigChangedEvent(this.id, configKey.value(), oldValue.value(), newValue.value(), operator));
    }

    public void updateDescription(String description) {
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }
}
