package com.bone.metadata.sdk.test.domain.query;

import java.time.LocalDateTime;

/**
 * 简化的用户分页查询类
 * 移除了所有外部依赖
 */
public class UserPageQuery {
    
    private Long id;
    private String userName;
    private String roleName;
    private String permCode;
    private String permName;
    private String permPath;
    private Integer pageNumber = 1;
    private Integer pageSize = 10;
    
    // 简单的构造器
    public UserPageQuery() {
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
    
    public String getRoleName() {
        return roleName;
    }
    
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
    
    public String getPermCode() {
        return permCode;
    }
    
    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }
    
    public String getPermName() {
        return permName;
    }
    
    public void setPermName(String permName) {
        this.permName = permName;
    }
    
    public String getPermPath() {
        return permPath;
    }
    
    public void setPermPath(String permPath) {
        this.permPath = permPath;
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
}