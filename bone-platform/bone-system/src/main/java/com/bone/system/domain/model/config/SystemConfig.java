package com.bone.system.domain.model.config;

import com.bone.core.domain.AggregateRoot;
import com.bone.system.domain.model.config.event.ConfigChangedEvent;
import com.bone.system.domain.model.config.event.ConfigCreatedEvent;
import com.bone.system.domain.model.config.vo.ConfigId;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.model.config.vo.ConfigType;
import com.bone.system.domain.model.config.vo.ConfigValue;
import com.bone.core.util.DistributedIdGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统配置聚合根
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemConfig extends AggregateRoot<ConfigId> {
    private ConfigId id;
    private Long dbId;
    private ConfigKey configKey;
    private ConfigValue configValue;
    private String description;
    private ConfigType configType;
    private boolean encrypted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 创建系统配置
     *
     * @param configKey   配置键
     * @param configValue 配置值
     * @param description 描述
     * @param configType  配置类型
     * @param encrypted   是否加密
     * @return 系统配置
     */
    public static SystemConfig create(ConfigKey configKey, ConfigValue configValue, 
                                      String description, ConfigType configType, boolean encrypted) {
        SystemConfig config = new SystemConfig();
        config.id = ConfigId.of(DistributedIdGenerator.generateUuid());
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

    /**
     * 更新配置值
     *
     * @param newValue 新值
     * @param operator 操作人
     */
    public void updateValue(ConfigValue newValue, String operator) {
        ConfigValue oldValue = this.configValue;
        this.configValue = newValue;
        this.updateTime = LocalDateTime.now();
        addDomainEvent(new ConfigChangedEvent(this.id, configKey.value(), oldValue.value(), newValue.value(), operator));
    }

    /**
     * 更新描述
     *
     * @param description 描述
     */
    public void updateDescription(String description) {
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 设置DB ID（供SDK回填使用）
     *
     * @param dbId DB ID值
     */
    void setDbId(Long dbId) {
        this.dbId = dbId;
    }
}
