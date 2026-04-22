package com.bone.blueprint.domain.service;

import com.bone.blueprint.domain.client.UserClient;
import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.vo.EncryptedPassword;
import com.bone.blueprint.domain.model.user.vo.Nickname;
import com.bone.blueprint.domain.model.user.vo.Username;
import com.bone.blueprint.domain.repository.UserRepository;
import com.bone.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户领域服务
 * <p>
 * 处理用户相关的核心业务逻辑
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserDomainService {
    private final UserRepository userRepository;
    private final UserClient userClient;
    
    /**
     * 创建用户
     */
    public User createUser(Username username, String rawPassword, Nickname nickname) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            throw new BizException("用户名已存在");
        }
        
        // 加密密码
        EncryptedPassword encryptedPassword = userClient.encryptPassword(rawPassword);
        
        // 创建用户
        User user = User.register(username, encryptedPassword, nickname);
        
        // 保存用户
        userRepository.save(user);
        return user;
    }
    
    /**
     * 更新用户信息
     */
    public User updateUser(Long id, Nickname nickname) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        
        user.update(nickname);
        userRepository.save(user);
        return user;
    }
    
    /**
     * 禁用用户
     */
    public User disableUser(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        
        user.disable();
        userRepository.save(user);
        return user;
    }
    
    /**
     * 启用用户
     */
    public User enableUser(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        
        user.enable();
        userRepository.save(user);
        return user;
    }
    
    /**
     * 根据用户名查找用户
     */
    public User findByUsername(Username username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }
    
    /**
     * 验证用户密码
     */
    public boolean verifyPassword(User user, String rawPassword) {
        return userClient.verifyPassword(rawPassword, user.getPassword());
    }
}