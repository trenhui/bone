package com.bone.blueprint.domain.model.user.vo;

import com.bone.core.exception.BizException;

/**
 * 昵称值对象
 * <p>
 * 确保昵称的有效性和一致性
 * </p>
 */
public record Nickname(String value) {
    public Nickname {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException("昵称不能为空");
        }
        if (value.length() > 50) {
            throw new BizException("昵称长度不能超过50个字符");
        }
    }
    
    public static Nickname of(String value) {
        return new Nickname(value);
    }
}