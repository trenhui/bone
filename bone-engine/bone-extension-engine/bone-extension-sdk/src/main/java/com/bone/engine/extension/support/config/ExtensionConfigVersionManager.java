package com.bone.engine.extension.support.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展配置版本管理
 * 支持配置版本管理和回滚
 */
@Slf4j
@Component
public class ExtensionConfigVersionManager {

    // 配置版本存储
    private final Map<String, List<ConfigVersion>> versionHistory = new ConcurrentHashMap<>();
    // 最大版本数量
    private static final int MAX_VERSIONS = 50;

    /**
     * 记录配置变更
     * @param configKey 配置键
     * @param oldValue 旧值
     * @param newValue 新值
     * @param operator 操作人
     * @return 版本号
     */
    public int recordConfigChange(String configKey, String oldValue, String newValue, String operator) {
        List<ConfigVersion> versions = versionHistory.computeIfAbsent(configKey, k -> new ArrayList<>());
        
        // 创建新版本
        int version = versions.size() + 1;
        ConfigVersion configVersion = new ConfigVersion();
        configVersion.setVersion(version);
        configVersion.setConfigKey(configKey);
        configVersion.setOldValue(oldValue);
        configVersion.setNewValue(newValue);
        configVersion.setOperator(operator);
        configVersion.setTimestamp(LocalDateTime.now());
        configVersion.setOperation("UPDATE");
        
        // 添加到版本历史
        versions.add(configVersion);
        
        // 限制版本数量
        if (versions.size() > MAX_VERSIONS) {
            versions.remove(0);
        }
        
        log.info("Config change recorded: key={}, version={}, operator={}", configKey, version, operator);
        return version;
    }

    /**
     * 获取配置版本历史
     * @param configKey 配置键
     * @return 版本历史列表
     */
    public List<ConfigVersion> getVersionHistory(String configKey) {
        return versionHistory.getOrDefault(configKey, new ArrayList<>());
    }

    /**
     * 回滚配置到指定版本
     * @param configKey 配置键
     * @param version 版本号
     * @param operator 操作人
     * @return 回滚后的值
     */
    public String rollbackToVersion(String configKey, int version, String operator) {
        List<ConfigVersion> versions = versionHistory.get(configKey);
        if (versions == null || version <= 0 || version > versions.size()) {
            log.warn("Invalid version for rollback: key={}, version={}", configKey, version);
            return null;
        }
        
        // 获取目标版本
        ConfigVersion targetVersion = versions.get(version - 1);
        String rollbackValue = targetVersion.getNewValue();
        
        // 记录回滚操作
        ConfigVersion rollbackVersion = new ConfigVersion();
        rollbackVersion.setVersion(versions.size() + 1);
        rollbackVersion.setConfigKey(configKey);
        rollbackVersion.setOldValue(getCurrentValue(configKey));
        rollbackVersion.setNewValue(rollbackValue);
        rollbackVersion.setOperator(operator);
        rollbackVersion.setTimestamp(LocalDateTime.now());
        rollbackVersion.setOperation("ROLLBACK");
        
        versions.add(rollbackVersion);
        
        log.info("Config rolled back: key={}, version={}, operator={}", configKey, version, operator);
        return rollbackValue;
    }

    /**
     * 获取当前配置值
     * @param configKey 配置键
     * @return 当前值
     */
    private String getCurrentValue(String configKey) {
        List<ConfigVersion> versions = versionHistory.get(configKey);
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        return versions.get(versions.size() - 1).getNewValue();
    }

    /**
     * 获取所有配置键
     * @return 配置键列表
     */
    public List<String> getAllConfigKeys() {
        return new ArrayList<>(versionHistory.keySet());
    }

    /**
     * 配置版本实体
     */
    @Data
    public static class ConfigVersion {
        private int version;
        private String configKey;
        private String oldValue;
        private String newValue;
        private String operator;
        private LocalDateTime timestamp;
        private String operation; // UPDATE, ROLLBACK
    }
}
