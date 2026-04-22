package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.vo.ConfigKey;

import java.util.Optional;

public interface SystemConfigRepository extends Repository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKey(ConfigKey configKey);
    boolean existsByConfigKey(ConfigKey configKey);
}
