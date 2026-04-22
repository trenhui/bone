package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.vo.UserId;
import com.bone.metadata.sdk.Repository;

/**
 * 用户仓库接口
 * <p>
 * 定义用户聚合根的持久化操作
 * </p>
 */
public interface UserRepository extends Repository<User, UserId> {
    // 只允许继承来的 save, remove, findById
    // 禁止添加查询方法
}