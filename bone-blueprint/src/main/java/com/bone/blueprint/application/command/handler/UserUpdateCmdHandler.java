package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.UserUpdateCmd;
import com.bone.blueprint.domain.model.user.vo.Nickname;
import com.bone.blueprint.domain.service.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 更新用户命令处理器
 * <p>
 * 处理更新用户的命令，执行领域逻辑
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UserUpdateCmdHandler {
    private final UserDomainService userDomainService;
    
    /**
     * 处理更新用户命令
     */
    public Long handle(UserUpdateCmd cmd) {
        // 转换命令参数为领域对象
        Nickname nickname = new Nickname(cmd.getNickname());
        
        // 执行领域逻辑
        return userDomainService.updateUser(cmd.getId(), nickname).getId();
    }
}