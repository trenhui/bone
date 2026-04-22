package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateUserCommand;
import com.bone.blueprint.domain.gateway.PasswordEncoder;
import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.vo.Password;
import com.bone.blueprint.domain.model.user.vo.UserId;
import com.bone.blueprint.domain.model.user.vo.Username;
import com.bone.blueprint.domain.repository.UserRepository;
import com.bone.blueprint.domain.service.user.UserUniquenessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建用户命令处理器
 * <p>
 * 处理创建用户的命令
 * </p>
 */
@Component
@RequiredArgsConstructor
public class CreateUserCommandHandler {
    private final UserRepository userRepository;
    private final UserUniquenessChecker userUniquenessChecker;
    private final PasswordEncoder passwordEncoder;

    /**
     * 处理创建用户命令
     * 
     * @param cmd 创建用户命令
     * @return 用户ID
     */
    @Transactional
    public UserId handle(CreateUserCommand cmd) {
        // 1. 转换为值对象
        Username username = Username.of(cmd.getUsername());
        
        // 2. 检查用户名唯一性
        userUniquenessChecker.check(username);
        
        // 3. 加密密码
        Password password = Password.of(cmd.getPassword(), passwordEncoder);
        
        // 4. 创建用户聚合根
        User user = User.register(username, password, cmd.getNickname());
        
        // 5. 保存用户
        userRepository.save(user);
        
        // 6. 返回用户ID
        return user.getId();
    }
}