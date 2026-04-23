package com.bone.system.application.command.handler;

import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.command.cmd.CreateConfigCmd;
import com.bone.system.application.command.cmd.UpdateConfigCmd;
import com.bone.system.common.exception.BusinessException;
import com.bone.system.common.exception.NotFoundException;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.model.config.vo.ConfigType;
import com.bone.system.domain.model.config.vo.ConfigValue;
import com.bone.system.domain.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConfigCommandHandler {
    private final SystemConfigRepository systemConfigRepository;

    @Transactional
    public Long handle(CreateConfigCmd cmd) {
        ConfigKey configKey = ConfigKey.of(cmd.getConfigKey());

        if (systemConfigRepository.existsByConfigKey(configKey)) {
            throw new BusinessException("配置键已存在: " + cmd.getConfigKey());
        }

        SystemConfig config = SystemConfig.create(
                configKey,
                ConfigValue.of(cmd.getConfigValue()),
                cmd.getDescription(),
                ConfigType.fromString(cmd.getConfigType()),
                cmd.isEncrypted()
        );

        systemConfigRepository.save(config);
        return config.getDbId();
    }

    @Transactional
    public void handle(UpdateConfigCmd cmd) {
        // The repository is keyed by ConfigId (UUID), but we have Long dbId from API.
        // This requires a custom query to find by dbId - let's implement it with the DSL.
        SystemConfig config = QueryBuilder.from(SystemConfig.class)
                .where(SystemConfig::getDbId).eq(cmd.getId())
                .single();
        if (config == null) {
            throw new NotFoundException("配置不存在: " + cmd.getId());
        }

        if (cmd.getConfigValue() != null) {
            config.updateValue(ConfigValue.of(cmd.getConfigValue()), "admin");
        }

        if (cmd.getDescription() != null) {
            config.updateDescription(cmd.getDescription());
        }

        systemConfigRepository.save(config);
    }

    @Transactional
    public void delete(Long id) {
        // Find by dbId first, then delete
        SystemConfig config = QueryBuilder.from(SystemConfig.class)
                .where(SystemConfig::getDbId).eq(id)
                .single();
        if (config != null) {
            systemConfigRepository.deleteById(config.getId());
        }
    }
}
