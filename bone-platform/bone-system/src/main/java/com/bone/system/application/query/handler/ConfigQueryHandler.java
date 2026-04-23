package com.bone.system.application.query.handler;

import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.qry.ConfigPageQry;
import com.bone.system.common.result.PageResult;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConfigQueryHandler {
    private final SystemConfigRepository systemConfigRepository;

    @Transactional(readOnly = true)
    public ConfigDTO getById(Long id) {
        // Find by dbId (Long) using DSL since repository is keyed by ConfigId (UUID)
        SystemConfig config = QueryBuilder.from(SystemConfig.class)
                .where(SystemConfig::getDbId).eq(id)
                .single();
        return config != null ? toDTO(config) : null;
    }

    @Transactional(readOnly = true)
    public ConfigDTO getByKey(String key) {
        return systemConfigRepository.findByConfigKey(
                        com.bone.system.domain.model.config.vo.ConfigKey.of(key))
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResult<ConfigDTO> page(ConfigPageQry qry) {
        FluentQuery<SystemConfig> query = QueryBuilder.from(SystemConfig.class);

        if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
            query.where(SystemConfig::getConfigKey).like(qry.getKeyword())
                    .or(SystemConfig::getDescription).like(qry.getKeyword());
        }

        com.bone.core.model.PageResult<SystemConfig> result = query.page(qry.getPageNum(), qry.getPageSize());

        List<ConfigDTO> dtoList = result.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    private ConfigDTO toDTO(SystemConfig config) {
        return ConfigDTO.builder()
                .id(config.getDbId())
                .configKey(config.getConfigKey().value())
                .configValue(config.isEncrypted() ? "******" : config.getConfigValue().value())
                .description(config.getDescription())
                .configType(config.getConfigType().name())
                .encrypted(config.isEncrypted())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
