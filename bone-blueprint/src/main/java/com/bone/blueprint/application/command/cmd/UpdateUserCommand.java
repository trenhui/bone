package com.bone.blueprint.application.command.cmd;

import com.bone.blueprint.domain.model.user.vo.UserId;
import lombok.Data;

/**
 * 更新用户命令
 * <p>
 * 用于更新用户信息的命令对象
 * </p>
 */
@Data
public class UpdateUserCommand {
    /**
     * 用户ID
     */
    private UserId id;
    
    /**
     * 昵称
     */
    private String nickname;
}