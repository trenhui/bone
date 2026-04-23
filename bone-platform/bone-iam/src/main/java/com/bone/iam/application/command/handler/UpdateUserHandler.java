package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.UpdateUserCmd;
import com.bone.iam.domain.model.user.User;
import com.bone.iam.domain.model.user.vo.Email;
import com.bone.iam.domain.model.user.vo.UserId;
import com.bone.iam.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UpdateUserHandler {
    private final UserRepository userRepository;

    @Transactional
    public void handle(UpdateUserCmd cmd) {
        UserId userId = UserId.of(cmd.getId());
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        Email email = Email.of(cmd.getEmail());
        user.updateEmail(email);
        userRepository.save(user);
    }
}