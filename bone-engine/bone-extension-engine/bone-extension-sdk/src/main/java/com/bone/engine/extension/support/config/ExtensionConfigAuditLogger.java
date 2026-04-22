package com.bone.engine.extension.support.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 扩展配置审计日志
 * 实现配置变更的审计日志
 */
@Slf4j
@Component
public class ExtensionConfigAuditLogger {

    // 审计日志存储
    private final List<AuditLog> auditLogs = new CopyOnWriteArrayList<>();
    // 最大日志数量
    private static final int MAX_LOGS = 1000;

    /**
     * 记录配置审计日志
     * @param configKey 配置键
     * @param operation 操作类型
     * @param oldValue 旧值
     * @param newValue 新值
     * @param operator 操作人
     * @param ipAddress IP地址
     */
    public void recordAuditLog(String configKey, String operation, String oldValue, String newValue, String operator, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(System.currentTimeMillis());
        auditLog.setConfigKey(configKey);
        auditLog.setOperation(operation);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);
        auditLog.setOperator(operator);
        auditLog.setIpAddress(ipAddress);
        auditLog.setTimestamp(LocalDateTime.now());
        
        // 添加到审计日志
        auditLogs.add(auditLog);
        
        // 限制日志数量
        if (auditLogs.size() > MAX_LOGS) {
            auditLogs.remove(0);
        }
        
        log.info("Config audit log recorded: operation={}, key={}, operator={}", operation, configKey, operator);
    }

    /**
     * 获取审计日志
     * @param limit 限制数量
     * @return 审计日志列表
     */
    public List<AuditLog> getAuditLogs(int limit) {
        int size = auditLogs.size();
        int start = Math.max(0, size - limit);
        return new ArrayList<>(auditLogs.subList(start, size));
    }

    /**
     * 根据配置键获取审计日志
     * @param configKey 配置键
     * @param limit 限制数量
     * @return 审计日志列表
     */
    public List<AuditLog> getAuditLogsByConfigKey(String configKey, int limit) {
        List<AuditLog> result = new ArrayList<>();
        for (int i = auditLogs.size() - 1; i >= 0 && result.size() < limit; i--) {
            AuditLog log = auditLogs.get(i);
            if (configKey.equals(log.getConfigKey())) {
                result.add(log);
            }
        }
        return result;
    }

    /**
     * 根据操作人获取审计日志
     * @param operator 操作人
     * @param limit 限制数量
     * @return 审计日志列表
     */
    public List<AuditLog> getAuditLogsByOperator(String operator, int limit) {
        List<AuditLog> result = new ArrayList<>();
        for (int i = auditLogs.size() - 1; i >= 0 && result.size() < limit; i--) {
            AuditLog log = auditLogs.get(i);
            if (operator.equals(log.getOperator())) {
                result.add(log);
            }
        }
        return result;
    }

    /**
     * 清空审计日志
     */
    public void clearAuditLogs() {
        auditLogs.clear();
        log.info("Audit logs cleared");
    }

    /**
     * 审计日志实体
     */
    @Data
    public static class AuditLog {
        private long id;
        private String configKey;
        private String operation;
        private String oldValue;
        private String newValue;
        private String operator;
        private String ipAddress;
        private LocalDateTime timestamp;
    }
}
