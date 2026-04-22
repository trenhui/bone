package com.bone.blueprint.domain.model.user.vo;

import com.bone.core.exception.BizException;

/**
 * 加密密码值对象
 * <p>
 * 确保密码的安全性和一致性
 * </p>
 */
public record EncryptedPassword(String value) {
    public EncryptedPassword {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException("密码不能为空");
        }
        if (value.length() < 64) {
            throw new BizException("密码必须是加密后的哈希值");
        }
    }
    
    public static EncryptedPassword of(String value) {
        return new EncryptedPassword(value);
    }
}