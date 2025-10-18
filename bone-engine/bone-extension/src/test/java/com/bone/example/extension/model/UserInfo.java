package com.bone.example.extension.model;

import lombok.Builder;
import lombok.Data;
import java.util.Date;

/**
 * 用户信息模型类
 * 存储用户的基本信息和详细属性
 */
@Data
@Builder
public class UserInfo {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 用户类型
     * 例如：INDIVIDUAL（个人用户）、ENTERPRISE（企业用户）、VIP（VIP用户）
     */
    private String userType;
    
    /**
     * 手机号码
     */
    private String phoneNumber;
    
    /**
     * 电子邮箱
     */
    private String email;
    
    /**
     * 用户状态
     * 例如：ACTIVE（活跃）、INACTIVE（非活跃）、DISABLED（已禁用）
     */
    private String status;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 最后登录时间
     */
    private Date lastLoginTime;
    
    /**
     * 备注信息
     */
    private String remarks;
}