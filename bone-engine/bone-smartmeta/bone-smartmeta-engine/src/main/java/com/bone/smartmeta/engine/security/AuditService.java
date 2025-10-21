package com.bone.smartmeta.engine.security;

/**
 * 审计服务接口
 * 负责记录系统中的安全相关操作日志
 */
public interface AuditService {
    
    /**
     * 记录访问拒绝日志
     * @param authentication 认证信息
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param operation 操作类型
     */
    void logAccessDenied(CustomAuthentication authentication, String resourceType, String resourceId, String operation);
    
    /**
     * 记录字段访问日志
     * @param authentication 认证信息
     * @param entityName 实体名称
     * @param fieldName 字段名称
     * @param operation 操作类型
     * @param isMasked 是否脱敏
     */
    void logFieldAccess(CustomAuthentication authentication, String entityName, String fieldName, String operation, boolean isMasked);
    
    /**
     * 记录记录访问日志
     * @param authentication 认证信息
     * @param entityName 实体名称
     * @param recordId 记录ID
     * @param operation 操作类型
     */
    void logRecordAccess(CustomAuthentication authentication, String entityName, String recordId, String operation);
    
    /**
     * 记录敏感操作日志
     * @param authentication 认证信息
     * @param operation 操作类型
     * @param details 操作详情
     */
    void logSensitiveOperation(CustomAuthentication authentication, String operation, String details);
    
    /**
     * 记录批量操作日志
     * @param authentication 认证信息
     * @param entityName 实体名称
     * @param operation 操作类型
     * @param recordCount 记录数量
     * @param successCount 成功数量
     */
    void logBatchOperation(CustomAuthentication authentication, String entityName, String operation, int recordCount, int successCount);
    
    /**
     * 记录认证日志
     * @param username 用户名
     * @param ipAddress IP地址
     * @param success 是否成功
     * @param reason 原因
     */
    void logAuthentication(String username, String ipAddress, boolean success, String reason);
    
    /**
     * 记录授权日志
     * @param authentication 认证信息
     * @param resource 资源
     * @param operation 操作
     * @param success 是否成功
     */
    void logAuthorization(CustomAuthentication authentication, String resource, String operation, boolean success);
}