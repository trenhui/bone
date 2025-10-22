package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体权限元数据模型类
 * 用于定义实体的访问权限控制策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityPermissionMetadata {
    // 基础权限配置
    private boolean publicRead = false;
    private boolean publicWrite = false;
    private boolean requireAuthentication = true;
    
    // 角色权限映射
    @Builder.Default
    private Map<String, PermissionLevel> rolePermissions = new HashMap<>();
    
    // 字段级权限配置
    @Builder.Default
    private Map<String, FieldPermissionMetadata> fieldPermissions = new HashMap<>();
    
    // 条件权限规则
    @Builder.Default
    private List<ConditionalPermissionRule> conditionalRules = new ArrayList<>();
    
    // 共享规则
    @Builder.Default
    private List<SharingRule> sharingRules = new ArrayList<>();
    
    // 审计配置
    private boolean auditAllOperations = true;
    private boolean auditFieldChanges = true;
    
    // 数据隔离级别
    private DataIsolationLevel dataIsolationLevel = DataIsolationLevel.TENANT;
    
    /**
     * 权限级别枚举
     */
    public enum PermissionLevel {
        NONE,      // 无权限
        READ,      // 只读权限
        CREATE,    // 创建权限
        UPDATE,    // 更新权限
        DELETE,    // 删除权限
        MANAGE     // 管理权限（全部）
    }
    
    /**
     * 数据隔离级别枚举
     */
    public enum DataIsolationLevel {
        GLOBAL,    // 全局共享
        TENANT,    // 租户隔离
        USER,      // 用户隔离
        CUSTOM     // 自定义隔离
    }
    
    /**
     * 字段权限元数据内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldPermissionMetadata {
        private String fieldName;
        private boolean readable = true;
        private boolean writable = true;
        private boolean required = false;
        private String accessFilterExpression;
    }
    
    /**
     * 条件权限规则内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionalPermissionRule {
        private String ruleName;
        private String condition;
        private PermissionLevel permissionLevel;
        private List<String> allowedOperations;
    }
    
    /**
     * 共享规则内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharingRule {
        private String ruleName;
        private String sourceRole;
        private String targetRole;
        private PermissionLevel permissionLevel;
        private String filterCondition;
    }
    
    /**
     * 添加角色权限
     */
    public void addRolePermission(String roleName, PermissionLevel permissionLevel) {
        if (rolePermissions == null) {
            rolePermissions = new HashMap<>();
        }
        rolePermissions.put(roleName, permissionLevel);
    }
    
    /**
     * 获取角色权限
     */
    public PermissionLevel getRolePermission(String roleName) {
        if (rolePermissions == null) {
            return null;
        }
        return rolePermissions.get(roleName);
    }
    
    /**
     * 添加字段权限
     */
    public void addFieldPermission(String fieldName, FieldPermissionMetadata permission) {
        if (fieldPermissions == null) {
            fieldPermissions = new HashMap<>();
        }
        fieldPermissions.put(fieldName, permission);
    }
    
    /**
     * 获取字段权限
     */
    public FieldPermissionMetadata getFieldPermission(String fieldName) {
        if (fieldPermissions == null) {
            return null;
        }
        return fieldPermissions.get(fieldName);
    }
}