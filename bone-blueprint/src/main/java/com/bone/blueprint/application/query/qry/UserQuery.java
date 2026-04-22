package com.bone.blueprint.application.query.qry;

import lombok.Data;

/**
 * 用户查询对象
 * <p>
 * 用于查询单个用户信息的查询对象
 * </p>
 */
@Data
public class UserQuery {
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 用户名
     */
    private String username;
}