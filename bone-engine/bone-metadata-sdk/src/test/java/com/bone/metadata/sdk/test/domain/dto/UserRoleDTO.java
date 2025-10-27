package com.bone.metadata.sdk.test.domain.dto;

/**
 * 简化的用户角色DTO类
 * 移除了所有外部依赖
 */
public class UserRoleDTO {
    
    private Long id;
    private String name;
    private String role;
    
    // 构造器
    public UserRoleDTO() {
    }
    
    public UserRoleDTO(Long id, String name, String role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
}