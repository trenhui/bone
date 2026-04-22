package com.bone.system.application.query.handler;

import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.qry.ConfigPageQry;
import com.bone.system.common.result.PageResult;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ConfigQueryHandler {
    private final SystemConfigRepository systemConfigRepository;

    @Transactional(readOnly = true)
    public ConfigDTO getById(Long id) {
        return systemConfigRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
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
        QueryBuilder<SystemConfig> queryBuilder = QueryBuilder.from(SystemConfig.class);
        
        if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
            queryBuilder = queryBuilder.where("configKey").like(qry.getKeyword())
                    .or("description").like(qry.getKeyword());
        }

        return queryBuilder
                .page(qry.getPageNum(), qry.getPageSize())
                .mapTo(ConfigDTO.class);
    }

    private ConfigDTO toDTO(SystemConfig config) {
        return ConfigDTO.builder()
                .id(config.getId())
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
