package com.bone.metadata.sdk.test.domain;

import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;

import java.util.Date;

/**
 * 简化的User类，不依赖外部AbstractEntity类
 */
@Data
@Table("user")
public class User {
    private Long id;
    private String name;
    private Long roleId;
    private Date createTime;
    private Long createBy;
    private Date updateTime;
    private Long updateBy;
    private Boolean deleted;
    
    // 无参构造器
    public User() {
    }
    
    // 简化的构造函数
    public User(Long id, String name, Long roleId) {
        this.id = id;
        this.name = name;
        this.roleId = roleId;
    }
    
    // 为了支持基本类型long参数
    public void setRoleId(long roleId) {
        this.roleId = roleId;
    }
}