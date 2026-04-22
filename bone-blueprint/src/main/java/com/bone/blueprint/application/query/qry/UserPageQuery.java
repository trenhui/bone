package com.bone.blueprint.application.query.qry;

import lombok.Data;

/**
 * 用户分页查询对象
 * <p>
 * 用于分页查询用户列表
 * </p>
 */
@Data
public class UserPageQuery {
    /**
     * 页码
     */
    private int pageNum = 1;
    
    /**
     * 每页大小
     */
    private int pageSize = 10;
    
    /**
     * 关键词
     */
    private String keyword;
}