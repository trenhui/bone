package com.bone.smartmeta.engine.security;

import java.util.List;

/**
 * 自定义认证接口，用于替换Spring Security的Authentication
 */
public interface CustomAuthentication {
    /**
     * 获取用户名
     */
    String getName();
    
    /**
     * 获取用户拥有的权限列表
     */
    List<String> getAuthorities();
    
    /**
     * 检查用户是否拥有指定角色
     */
    boolean hasRole(String role);
}