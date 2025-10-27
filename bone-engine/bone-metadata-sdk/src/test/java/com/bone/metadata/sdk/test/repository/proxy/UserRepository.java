package com.bone.metadata.sdk.test.repository.proxy;

import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.domain.dto.UserRoleDTO;
import com.bone.metadata.sdk.test.domain.dto.UserWithRoleDTO;
import com.bone.metadata.sdk.test.domain.query.UserPageQuery;
import com.bone.metadata.sdk.test.domain.query.UserQuery;
import com.bone.metadata.sdk.test.domain.request.UserSearchRequest;

import java.util.List;
import java.util.Optional;

/**
 * 简化的用户数据访问接口
 * 移除了所有外部依赖
 */
public interface UserRepository {

    /**
     * 保存用户
     */
    User save(User user);

    /**
     * 根据ID查找用户
     */
    Optional<User> findById(Long id);

    /**
     * 查询所有用户
     */
    List<User> findAll();

    /**
     * 删除用户
     */
    void deleteById(Long id);

    /**
     * 根据姓名模糊查询用户
     */
    List<User> findByName(String name);

    /**
     * 根据角色ID查询用户
     */
    List<User> findByRoleId(Long roleId);

    /**
     * 查询用户及角色信息（简化版）
     */
    List<UserWithRoleDTO> findUsersWithRole(UserQuery query);

    /**
     * 分页查询用户
     */
    List<UserWithRoleDTO> findUsersByPage(UserPageQuery query);

    /**
     * 根据搜索条件查询用户
     */
    List<UserRoleDTO> searchUsers(UserSearchRequest request);
}