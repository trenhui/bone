package com.bone.metadata.sdk.test.domain.query;

import java.time.LocalDateTime;

/**
 * 简化的用户查询类
 * 移除了所有外部依赖
 */
public class UserQuery {
    
    private Long id;
    private String userName;
    private Long roleId;
    
    // 简单的构造器
    public UserQuery() {
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public Long getRoleId() {
        return roleId;
    }
    
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}