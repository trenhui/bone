package com.bone.demo.domain.service;

import com.bone.demo.application.dto.query.UserQuery;
import com.bone.demo.application.dto.query.UserPageQuery;
import com.bone.demo.domain.model.User;
import com.bone.demo.domain.repository.UserRepository;
import com.bone.demo.infrastructure.config.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户服务
 */
@Slf4j
@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * 创建用户
     */
    public Long createUser(User user) {
        // 这里是示例实现，实际项目中需要添加密码加密等逻辑
        user.setCreateTime(System.currentTimeMillis());
        user.setUpdateTime(System.currentTimeMillis());
        return userRepository.save(user);
    }
    
    /**
     * 获取用户
     */
    public User getUser(Long id) {
        return userRepository.getById(id);
    }
    
    /**
     * 更新用户
     */
    public void updateUser(User user) {
        user.setUpdateTime(System.currentTimeMillis());
        userRepository.update(user);
    }
    
    /**
     * 删除用户
     */
    public void deleteUser(Long id) {
        userRepository.delete(id);
    }
    
    /**
     * 查询用户列表
     */
    public List<User> queryUser(UserQuery query) {
        // 这里是示例实现，实际项目中需要根据查询条件查询数据库
        return new ArrayList<>();
    }
    
    /**
     * 分页查询用户
     */
    public PageResult<User> queryUserPage(UserPageQuery query) {
        // 这里是示例实现，实际项目中需要根据分页条件查询数据库
        return new PageResult<>(Collections.emptyList(), 0L, query.getPageSize(), query.getPageNum());
    }
}