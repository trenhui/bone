package com.bone.metadata.sdk.test.domain.request;

/**
 * 简化的用户搜索请求类
 * 移除了所有外部依赖
 */
public class UserSearchRequest {
    
    private String name;
    private Long roleId;
    private Integer pageNumber = 1;
    private Integer pageSize = 20;
    private Integer offset;
    
    // 简单的构造器
    public UserSearchRequest() {
    }
    
    // Getter和Setter方法
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Long getRoleId() {
        return roleId;
    }
    
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
    
    public Integer getPageNumber() {
        return pageNumber;
    }
    
    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }
    
    public Integer getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
    
    public Integer getOffset() {
        return offset = (pageNumber - 1) * pageSize;
    }
    
    public void setOffset(Integer offset) {
        this.offset = offset;
    }
}