package com.bone.blueprint.application.command.cmd;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 更新用户命令
 * <p>
 * 用于更新用户信息的命令对象
 * </p>
 */
@Data
public class UserUpdateCmd {
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
}