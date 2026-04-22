package com.bone.system.domain.model.config.event;

import com.bone.core.domain.DomainEvent;
import com.bone.system.domain.model.config.vo.ConfigId;

import java.time.LocalDateTime;

public record ConfigChangedEvent(
        ConfigId configId,
        String configKey,
        String oldValue,
        String newValue,
        String operator,
        LocalDateTime eventTime
) implements DomainEvent {

    public ConfigChangedEvent(ConfigId configId, String configKey, String oldValue, String newValue, String operator) {
        this(configId, configKey, oldValue, newValue, operator, LocalDateTime.now());
    }
}
