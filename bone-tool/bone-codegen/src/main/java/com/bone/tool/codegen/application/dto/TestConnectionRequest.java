package com.bone.tool.codegen.application.dto;

import lombok.Data;

/**
 * 测试数据源连接请求参数
 * <p>
 * 用于测试数据库连接的有效性
 * 
 * @author bone-team
 */
@Data
public class TestConnectionRequest {
    
    /**
     * 数据源名称
     */
    private String name;
    
    /**
     * JDBC连接URL
     */
    private String url;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * JDBC驱动类名
     */
    private String driverClassName;
}