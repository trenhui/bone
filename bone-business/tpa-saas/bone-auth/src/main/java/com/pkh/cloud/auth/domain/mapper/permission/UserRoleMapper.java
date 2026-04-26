package com.pkh.cloud.auth.domain.mapper.permission;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pkh.cloud.auth.domain.entity.permission.UserRoleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {

    default List<UserRoleDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
    }

    default List<UserRoleDO> selectListByUserIds(Collection<Long> userIds) {
        return selectList(new LambdaQueryWrapper<UserRoleDO>().in(UserRoleDO::getUserId, userIds));
    }

    default void deleteListByUserIdAndRoleIdIds(Long userId, Collection<Long> roleIds) {
        delete(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId)
                .in(UserRoleDO::getRoleId, roleIds));
    }

    default void deleteListByUserId(Long userId) {
        delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
    }

    default void deleteListByRoleId(Long roleId) {
        delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getRoleId, roleId));
    }

    default List<UserRoleDO> selectListByRoleIds(Collection<Long> roleIds) {
        return selectList(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getRoleId, roleIds));
    }

}
