package com.bone.blueprint.application.command.handler.iam;

import com.bone.blueprint.application.command.cmd.iam.CreateUserCmd;
import com.bone.blueprint.domain.model.iam.User;
import com.bone.blueprint.domain.repository.iam.UserRepository;
import com.bone.blueprint.adapter.web.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateUserHandler {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Transactional
    public Long handle(CreateUserCmd cmd) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsernameAndTenantId(cmd.getUsername(), cmd.getTenantId())) {
            throw new InvalidRequestException("用户名已存在");
        }
        
        // 加密密码
        String passwordHash = passwordEncoder.encode(cmd.getPassword());
        
        // 创建用户
        User user = User.create(cmd.getUsername(), cmd.getEmail(), passwordHash, cmd.getTenantId());
        userRepository.save(user);
        
        return user.getId();
    }
}
