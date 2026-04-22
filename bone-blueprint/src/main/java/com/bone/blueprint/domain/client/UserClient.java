package com.bone.blueprint.domain.client;

import com.bone.blueprint.domain.model.user.vo.EncryptedPassword;

/**
 * 用户客户端接口
 * <p>
 * 定义用户相关的外部系统交互操作
 * </p>
 */
public interface UserClient {
    /**
     * 加密密码
     */
    EncryptedPassword encryptPassword(String rawPassword);
    
    /**
     * 验证密码
     */
    boolean verifyPassword(String rawPassword, EncryptedPassword encryptedPassword);
}