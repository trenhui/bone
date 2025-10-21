package com.bone.smartmeta.engine.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 权限检查器，用于检查用户对实体和字段的访问权限
 */
@Component
public class PermissionChecker {
    
    /**
     * 检查用户是否有实体访问权限
     * @param entityName 实体名称
     * @param authentication 用户认证信息
     * @param permissionType 权限类型（read, write, delete等）
     * @return 如果有权限则返回true，否则返回false
     */
    public boolean hasEntityAccessPermission(String entityName, CustomAuthentication authentication, String permissionType) {
        // 管理员角色拥有所有权限
        if (authentication.hasRole("ADMIN")) {
            return true;
        }
        
        // system用户拥有所有权限
        if ("system".equals(authentication.getName())) {
            return true;
        }
        
        // 实际项目中，这里应该从权限系统中检查用户对实体的权限
        // 例如：return permissionService.hasPermission(authentication.getName(), entityName, permissionType);
        
        // 为了测试方便，暂时允许所有用户访问所有实体
        return true;
    }
    
    /**
     * 检查用户是否有字段访问权限
     * @param entityName 实体名称
     * @param fieldName 字段名称
     * @param authentication 用户认证信息
     * @param permissionType 权限类型（read, write等）
     * @return 如果有权限则返回true，否则返回false
     */
    public boolean hasFieldAccessPermission(String entityName, String fieldName, CustomAuthentication authentication, String permissionType) {
        // 管理员角色拥有所有权限
        if (authentication.hasRole("ADMIN")) {
            return true;
        }
        
        // system用户拥有所有权限
        if ("system".equals(authentication.getName())) {
            return true;
        }
        
        // 实际项目中，这里应该从权限系统中检查用户对字段的权限
        // 例如：return permissionService.hasFieldPermission(authentication.getName(), entityName, fieldName, permissionType);
        
        // 为了测试方便，暂时允许所有用户访问所有字段
        return true;
    }
    
    /**
     * 获取用户可读的字段列表
     * @param entityName 实体名称
     * @param authentication 用户认证信息
     * @return 用户可读的字段列表
     */
    public List<String> getReadableFields(String entityName, CustomAuthentication authentication) {
        // 管理员角色和system用户可以读取所有字段（返回*）
        if (authentication.hasRole("ADMIN") || "system".equals(authentication.getName())) {
            return Arrays.asList("*");
        }
        
        // 实际项目中，这里应该从权限系统中获取用户可读的字段列表
        // 例如：return permissionService.getReadableFields(authentication.getName(), entityName);
        
        // 为了测试方便，暂时返回*表示可以读取所有字段
        return Arrays.asList("*");
    }
}
    
    /**
     * 检查用户是否有字段访问权限
     * @param entityName 实体名称
     * @param fieldName 字段名称
     * @param authentication 用户认证信息
     * @param permissionType 权限类型（read, write等）
     * @return 如果有权限则返回true，否则返回false
     */
    public boolean hasFieldAccessPermission(String entityName, String fieldName, CustomAuthentication authentication, String permissionType) {
        // 管理员角色拥有所有权限
        if (authentication.hasRole("ADMIN")) {
            return true;
        }
        
        // system用户拥有所有权限
        if ("system".equals(authentication.getName())) {
            return true;
        }
        
        // 实际项目中，这里应该从权限系统中检查用户对字段的权限
        // 例如：return permissionService.hasFieldPermission(authentication.getName(), entityName, fieldName, permissionType);
        
        // 为了测试方便，暂时允许所有用户访问所有字段
        return true;
    }
    
    /**
     * 获取用户可读的字段列表
     * @param entityName 实体名称
     * @param authentication 用户认证信息
     * @return 用户可读的字段列表
     */
    public List<String> getReadableFields(String entityName, CustomAuthentication authentication) {
        // 管理员角色和system用户可以读取所有字段（返回*）
        if (authentication.hasRole("ADMIN") || "system".equals(authentication.getName())) {
            return Arrays.asList("*");
        }
        
        // 实际项目中，这里应该从权限系统中获取用户可读的字段列表
        // 例如：return permissionService.getReadableFields(authentication.getName(), entityName);
        
        // 为了测试方便，暂时返回*表示可以读取所有字段
        return Arrays.asList("*");
    }
}
    
    /**
     * 检查用户是否有字段访问权限
     * @param entityName 实体名称
     * @param fieldName 字段名称
     * @param userId 用户ID
     * @param permissionType 权限类型（read, write等）
     * @return 如果有权限则返回true，否则返回false
     */
    public boolean hasFieldAccessPermission(String entityName, String fieldName, String userId, String permissionType) {
        // 简单实现：系统管理员和system用户拥有所有权限
        if ("admin".equals(userId) || "system".equals(userId)) {
            return true;
        }
        
        // 实际项目中，这里应该从权限系统中检查用户对字段的权限
        // 例如：return permissionService.hasFieldPermission(userId, entityName, fieldName, permissionType);
        
        // 为了测试方便，暂时允许所有用户访问所有字段
        return true;
    }
    
    /**
     * 获取用户可读的字段列表
     * @param entityName 实体名称
     * @param userId 用户ID
     * @return 用户可读的字段列表
     */
    public List<String> getReadableFields(String entityName, String userId) {
        // 简单实现：系统管理员和system用户可以读取所有字段（返回*）
        if ("admin".equals(userId) || "system".equals(userId)) {
            return Arrays.asList("*");
        }
        
        // 实际项目中，这里应该从权限系统中获取用户可读的字段列表
        // 例如：return permissionService.getReadableFields(userId, entityName);
        
        // 为了测试方便，暂时返回*表示可以读取所有字段
        return Arrays.asList("*");
    }
}
    
    /**
     * 检查用户是否有字段访问权限
     * @param entityName 实体名称
     * @param fieldName 字段名称
     * @param userId 用户ID
     * @param permissionType 权限类型（read, write等）
     * @return 如果有权限则返回true，否则返回false
     */
    public boolean hasFieldAccessPermission(String entityName, String fieldName, String userId, String permissionType) {
        // 简单实现：系统管理员和system用户拥有所有权限
        if ("admin".equals(userId) || "system".equals(userId)) {
            return true;
        }
        
        // 实际项目中，这里应该从权限系统中检查用户对字段的权限
        // 例如：return permissionService.hasFieldPermission(userId, entityName, fieldName, permissionType);
        
        // 为了测试方便，暂时允许所有用户访问所有字段
        return true;
    }
    
    /**
     * 获取用户可读的字段列表
     * @param entityName 实体名称
     * @param userId 用户ID
     * @return 用户可读的字段列表
     */
    public List<String> getReadableFields(String entityName, String userId) {
        // 简单实现：系统管理员和system用户可以读取所有字段（返回*）
        if ("admin".equals(userId) || "system".equals(userId)) {
            return Arrays.asList("*");
        }
        
        // 实际项目中，这里应该从权限系统中获取用户可读的字段列表
        // 例如：return permissionService.getReadableFields(userId, entityName);
        
        // 为了测试方便，暂时返回*表示可以读取所有字段
        return Arrays.asList("*");
    }
}