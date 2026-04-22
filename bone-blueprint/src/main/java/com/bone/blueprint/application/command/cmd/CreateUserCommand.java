package com.bone.blueprint.application.command.cmd;

import lombok.Data;

/**
 * 创建用户命令
 * <p>
 * 用于创建新用户的命令对象
 * </p>
 */
@Data
public class CreateUserCommand {
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 昵称
     */
    private String nickname;
}