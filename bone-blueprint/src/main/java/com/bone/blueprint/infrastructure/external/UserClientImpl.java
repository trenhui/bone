package com.bone.blueprint.infrastructure.external;

import com.bone.blueprint.domain.client.UserClient;
import com.bone.blueprint.domain.model.user.vo.EncryptedPassword;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 用户客户端实现
 * <p>
 * 处理密码加密和验证等外部系统交互操作
 * </p>
 */
@Component
public class UserClientImpl implements UserClient {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Override
    public EncryptedPassword encryptPassword(String rawPassword) {
        String encryptedPassword = passwordEncoder.encode(rawPassword);
        return EncryptedPassword.of(encryptedPassword);
    }
    
    @Override
    public boolean verifyPassword(String rawPassword, EncryptedPassword encryptedPassword) {
        return passwordEncoder.matches(rawPassword, encryptedPassword.value());
    }
}