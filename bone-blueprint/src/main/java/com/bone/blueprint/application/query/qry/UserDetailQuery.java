package com.bone.blueprint.application.query.qry;

import com.bone.blueprint.domain.model.user.vo.UserId;
import lombok.Data;

/**
 * 用户详情查询对象
 * <p>
 * 用于查询用户详情
 * </p>
 */
@Data
public class UserDetailQuery {
    /**
     * 用户ID
     */
    private UserId id;
    
    /**
     * 用户名
     */
    private String username;
}