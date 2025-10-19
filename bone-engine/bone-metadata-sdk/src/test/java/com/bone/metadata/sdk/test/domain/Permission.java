package com.bone.metadata.sdk.test.domain;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.extension.Extensible;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.model.NullableConcurrentMap;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Table("sys_permission")
public class Permission extends Entity<Long> implements Extensible {

    @Transient
    private final Map<String, Object> extraProperties = new NullableConcurrentMap<>(64);

    @Schema(description = "业务身份code")
    private String bizIdentityCode;

    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    private String permName;

    private String permCode;

    private Integer permType;

    private Long parentId;

    private String path;

    private String component;

    private String icon;

    private Integer sortOrder;

    // 创建时间：使用 LocalDateTime
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // 修改时间：使用 LocalDateTime
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "修改时间")
    private LocalDateTime updateTime;

    @Transient
    private Set<Role> roles = new HashSet<>();
    
    // 显式添加所有getter和setter方法
    public String getBizIdentityCode() {
        return bizIdentityCode;
    }
    
    public void setBizIdentityCode(String bizIdentityCode) {
        this.bizIdentityCode = bizIdentityCode;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPermName() {
        return permName;
    }
    
    public void setPermName(String permName) {
        this.permName = permName;
    }
    
    public String getPermCode() {
        return permCode;
    }
    
    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }
    
    public Integer getPermType() {
        return permType;
    }
    
    public void setPermType(Integer permType) {
        this.permType = permType;
    }
    
    public Long getParentId() {
        return parentId;
    }
    
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    public Integer getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    public Set<Role> getRoles() {
        return roles;
    }
    
    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }
    
    // 实现Extensible接口的方法
    @Override
    public Map<String, ?> getExtraProperties() {
        return extraProperties;
    }
    
    @Override
    public void setExtraProperties(Map<String, ?> extraProperties) {
        this.extraProperties.clear();
        if (extraProperties != null) {
            this.extraProperties.putAll(extraProperties);
        }
    }
    
    @Override
    public void mergeExtraProperties(Map<String, ?> extraProperties) {
        if (extraProperties != null) {
            this.extraProperties.putAll(extraProperties);
        }
    }
    
    // 无参构造函数
    public Permission() {
        // 默认构造函数
    }
    
    // 全参构造函数
    public Permission(String bizIdentityCode, Long id, String permName, String permCode, 
                     Integer permType, Long parentId, String path, String component,
                     String icon, Integer sortOrder, LocalDateTime createTime, 
                     LocalDateTime updateTime, Set<Role> roles) {
        this.bizIdentityCode = bizIdentityCode;
        this.id = id;
        this.permName = permName;
        this.permCode = permCode;
        this.permType = permType;
        this.parentId = parentId;
        this.path = path;
        this.component = component;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.roles = roles != null ? roles : new HashSet<>();
    }
    
    // 重写equals和hashCode方法
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : super.hashCode();
    }
}