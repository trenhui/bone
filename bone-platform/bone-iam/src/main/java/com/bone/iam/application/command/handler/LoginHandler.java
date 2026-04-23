package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.LoginCmd;
import com.bone.iam.domain.service.AuthService;
import com.bone.iam.domain.model.user.User;
import com.bone.iam.common.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LoginHandler {
    private final AuthService authService;

    public Map<String, Object> handle(LoginCmd cmd) {
        User user = authService.authenticate(cmd.getUsername(), cmd.getPassword());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = JwtUtils.generateToken(user.getId().value(), user.getUsername().value());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }
}