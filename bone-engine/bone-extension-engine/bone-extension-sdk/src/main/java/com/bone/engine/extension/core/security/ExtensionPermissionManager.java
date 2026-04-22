package com.bone.engine.extension.core.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展权限管理器
 * 实现细粒度的权限控制，基于角色和资源
 */
@Slf4j
@Component
public class ExtensionPermissionManager {

    // 权限存储: role -> resource -> permission
    private final Map<String, Map<String, String>> permissions = new ConcurrentHashMap<>();

    /**
     * 授予权限
     * @param role 角色
     * @param resource 资源
     * @param permission 权限
     */
    public void grantPermission(String role, String resource, String permission) {
        Map<String, String> rolePermissions = permissions.computeIfAbsent(role, k -> new ConcurrentHashMap<>());
        rolePermissions.put(resource, permission);
        log.info("Permission granted: role={}, resource={}, permission={}", role, resource, permission);
    }

    /**
     * 检查权限
     * @param role 角色
     * @param resource 资源
     * @param requiredPermission 所需权限
     * @return 是否有权限
     */
    public boolean checkPermission(String role, String resource, String requiredPermission) {
        Map<String, String> rolePermissions = permissions.get(role);
        if (rolePermissions == null) {
            return false;
        }
        
        String permission = rolePermissions.get(resource);
        if (permission == null) {
            return false;
        }
        
        // 权限匹配逻辑
        return permission.equals(requiredPermission) || "ALL".equals(permission);
    }

    /**
     * 检查扩展执行权限
     * @param extension 扩展实现对象
     * @param method 要执行的方法
     * @return 是否有权限执行
     */
    public boolean checkPermission(Object extension, java.lang.reflect.Method method) {
        // 默认允许执行，实际项目中可以根据需要实现更严格的权限检查
        return true;
    }

    /**
     * 撤销权限
     * @param role 角色
     * @param resource 资源
     */
    public void revokePermission(String role, String resource) {
        Map<String, String> rolePermissions = permissions.get(role);
        if (rolePermissions != null) {
            rolePermissions.remove(resource);
            log.info("Permission revoked: role={}, resource={}", role, resource);
        }
    }

    /**
     * 获取角色的所有权限
     * @param role 角色
     * @return 权限映射
     */
    public Map<String, String> getPermissions(String role) {
        return permissions.getOrDefault(role, new ConcurrentHashMap<>());
    }

    /**
     * 初始化默认权限
     */
    public void initDefaultPermissions() {
        // 管理员角色
        grantPermission("ADMIN", "extension:manage", "ALL");
        grantPermission("ADMIN", "extension:execute", "ALL");
        grantPermission("ADMIN", "extension:config", "ALL");
        
        // 开发者角色
        grantPermission("DEVELOPER", "extension:execute", "ALL");
        grantPermission("DEVELOPER", "extension:config", "READ");
        
        // 普通用户角色
        grantPermission("USER", "extension:execute", "READ");
        
        log.info("Default permissions initialized");
    }
}
