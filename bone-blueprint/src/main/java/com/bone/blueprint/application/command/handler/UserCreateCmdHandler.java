package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.UserCreateCmd;
import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.event.UserCreatedEvent;
import com.bone.blueprint.domain.model.user.vo.Nickname;
import com.bone.blueprint.domain.model.user.vo.Username;
import com.bone.blueprint.domain.service.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建用户命令处理器
 * <p>
 * 处理创建用户的命令，执行领域逻辑并发布领域事件
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UserCreateCmdHandler {
    private final UserDomainService userDomainService;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 处理创建用户命令
     */
    @Transactional
    public Long handle(UserCreateCmd cmd) {
        // 转换命令参数为领域对象
        Username username = Username.of(cmd.getUsername());
        Nickname nickname = Nickname.of(cmd.getNickname());
        
        // 执行领域逻辑
        User user = userDomainService.createUser(username, cmd.getPassword(), nickname);
        
        // 发布领域事件
        eventPublisher.publishEvent(new UserCreatedEvent(this, user));
        
        return user.getId();
    }
}