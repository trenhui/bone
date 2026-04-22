package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.UpdateUserCommand;
import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.vo.UserId;
import com.bone.blueprint.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 更新用户命令处理器
 * <p>
 * 处理更新用户信息的命令
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UpdateUserCommandHandler {
    private final UserRepository userRepository;

    /**
     * 处理更新用户命令
     * 
     * @param cmd 更新用户命令
     * @return 用户ID
     */
    @Transactional
    public UserId handle(UpdateUserCommand cmd) {
        // 1. 查找用户
        User user = userRepository.findById(cmd.getId())
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        
        // 2. 更新用户信息
        user.update(cmd.getNickname());
        
        // 3. 保存用户
        userRepository.save(user);
        
        // 4. 返回用户ID
        return user.getId();
    }
}