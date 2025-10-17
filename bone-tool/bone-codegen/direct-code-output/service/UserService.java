package com.example.service;

import com.example.entity.User;
import java.util.List;
import java.util.Optional;

/**
 * 用户服务接口
 */
public interface UserService {
    /**
     * 创建用户
     */
    User createUser(User user);
    
    /**
     * 根据ID获取用户
     */
    Optional<User> getUserById(Long id);
    
    /**
     * 更新用户信息
     */
    User updateUser(User user);
    
    /**
     * 删除用户
     */
    void deleteUser(Long id);
    
    /**
     * 查询所有用户
     */
    List<User> listAllUsers();
}
