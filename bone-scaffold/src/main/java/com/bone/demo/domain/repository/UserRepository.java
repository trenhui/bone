package com.bone.demo.domain.repository;

import com.bone.demo.domain.model.User;

/**
 * 用户仓库接口
 */
public interface UserRepository {
    
    /**
     * 根据ID获取用户
     */
    User getById(Long id);
    
    /**
     * 保存用户
     */
    Long save(User user);
    
    /**
     * 更新用户
     */
    void update(User user);
    
    /**
     * 删除用户
     */
    void delete(Long id);
}