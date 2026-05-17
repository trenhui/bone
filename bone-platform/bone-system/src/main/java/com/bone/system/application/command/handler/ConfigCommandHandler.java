package com.bone.system.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.command.cmd.CreateConfigCmd;
import com.bone.system.application.command.cmd.UpdateConfigCmd;
import com.bone.system.common.exception.BusinessException;
import com.bone.system.common.exception.NotFoundException;
import com.bone.system.domain.config.SystemConfig;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.model.config.vo.ConfigType;
import com.bone.system.domain.model.config.vo.ConfigValue;
import com.bone.system.domain.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateSystemConfig",
    description = "创建系统配置",
    inputSchema = "{\"configKey\": \"string\", \"configValue\": \"string\", \"configType\": \"string\", \"description\": \"string\", \"encrypted\": \"boolean\"}",
    outputSchema = "{\"configId\": \"long\"}",
    idempotent = false,
    cost = 2,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class ConfigCommandHandler {
    private final SystemConfigRepository systemConfigRepository;

    @Transactional
    public Long handle(CreateConfigCmd cmd) {
        ConfigKey configKey = ConfigKey.of(cmd.getConfigKey());

        // 使用 QueryBuilder 构建查询条件
        SystemConfig existingConfig = QueryBuilder.from(SystemConfig.class)
                .where(SystemConfig::getConfigKey).eq(configKey)
                .single();
        if (existingConfig != null) {
            throw new BusinessException("配置键已存在: " + cmd.getConfigKey());
        }

        Long configId = DistributedIdGenerator.generateLongId();
        SystemConfig config = SystemConfig.create(
                configId,
                configKey,
                ConfigValue.of(cmd.getConfigValue()),
                cmd.getDescription(),
                ConfigType.fromString(cmd.getConfigType()),
                cmd.isEncrypted()
        );

        systemConfigRepository.save(config);
        return config.getId();
    }

    @Transactional
    public void handle(UpdateConfigCmd cmd) {
        SystemConfig config = QueryBuilder.from(SystemConfig.class)
                .where(SystemConfig::getId).eq(cmd.getId())
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
        SystemConfig config = QueryBuilder.from(SystemConfig.class)
                .where(SystemConfig::getId).eq(id)
                .single();
        if (config != null) {
            systemConfigRepository.deleteById(config.getId());
        }
    }
}
