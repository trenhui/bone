package com.bone.blueprint.domain.service.user;

import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.vo.Username;
import com.bone.metadata.sdk.query.QueryBuilder;

/**
 * 用户唯一性检查服务
 * <p>
 * 检查用户名是否唯一
 * </p>
 */
public class UserUniquenessChecker {
    /**
     * 检查用户名是否唯一
     * 
     * @param username 用户名
     * @throws IllegalArgumentException 如果用户名已存在
     */
    public void check(Username username) {
        boolean exists = QueryBuilder.from(User.class)
            .where("username").eq(username.getValue())
            .exists();
        if (exists) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }
}