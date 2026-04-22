package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.CreateUserCmd;
import com.bone.iam.domain.model.user.User;
import com.bone.iam.domain.model.user.vo.Email;
import com.bone.iam.domain.model.user.vo.UserId;
import com.bone.iam.domain.model.user.vo.Username;
import com.bone.iam.domain.repository.UserRepository;
import com.bone.iam.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateUserHandler {
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserId handle(CreateUserCmd cmd) {
        if (userService.isUsernameExists(cmd.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        if (userService.isEmailExists(cmd.getEmail())) {
            throw new RuntimeException("邮箱已存在");
        }

        Username username = Username.of(cmd.getUsername());
        Email email = Email.of(cmd.getEmail());
        String passwordHash = passwordEncoder.encode(cmd.getPassword());

        User user = User.register(username, passwordHash, email, cmd.getTenantId());
        userRepository.save(user);
        return user.getId();
    }
}