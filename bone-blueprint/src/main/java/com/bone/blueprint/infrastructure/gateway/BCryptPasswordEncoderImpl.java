package com.bone.blueprint.infrastructure.gateway;

import com.bone.blueprint.domain.gateway.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt密码编码器实现
 * <p>
 * 使用Spring Security的BCryptPasswordEncoder实现密码加密
 * </p>
 */
@Component
public class BCryptPasswordEncoderImpl implements PasswordEncoder {
    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordEncoderImpl() {
        this.encoder = new BCryptPasswordEncoder();
    }

    @Override
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}