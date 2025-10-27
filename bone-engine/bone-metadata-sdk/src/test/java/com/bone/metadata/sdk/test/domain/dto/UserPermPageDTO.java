package com.bone.metadata.sdk.test.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-权限分页查询结果 DTO
 */
public class UserPermPageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String userName;
    private String roleName;
    private String permCode;
    private String permName;
    private String permPath;
    private String bizCode;
    private Integer permType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 获取ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取用户名
     */
    public String getUserName() {
        return userName;
    }

    /**
     * 设置用户名
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * 获取角色名
     */
    public String getRoleName() {
        return roleName;
    }

    /**
     * 设置角色名
     */
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    /**
     * 获取权限编码
     */
    public String getPermCode() {
        return permCode;
    }

    /**
     * 设置权限编码
     */
    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }

    /**
     * 获取权限名称
     */
    public String getPermName() {
        return permName;
    }

    /**
     * 设置权限名称
     */
    public void setPermName(String permName) {
        this.permName = permName;
    }

    /**
     * 获取权限路径
     */
    public String getPermPath() {
        return permPath;
    }

    /**
     * 设置权限路径
     */
    public void setPermPath(String permPath) {
        this.permPath = permPath;
    }

    /**
     * 获取业务编码
     */
    public String getBizCode() {
        return bizCode;
    }

    /**
     * 设置业务编码
     */
    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    /**
     * 获取权限类型
     */
    public Integer getPermType() {
        return permType;
    }

    /**
     * 设置权限类型
     */
    public void setPermType(Integer permType) {
        this.permType = permType;
    }

    /**
     * 获取状态
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置状态
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPermPageDTO that = (UserPermPageDTO) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UserPermPageDTO{" +
                "id=" + id +
                ", userName='" + userName + '\'' +
                ", roleName='" + roleName + '\'' +
                ", permCode='" + permCode + '\'' +
                ", permName='" + permName + '\'' +
                ", permPath='" + permPath + '\'' +
                ", bizCode='" + bizCode + '\'' +
                ", permType=" + permType +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}