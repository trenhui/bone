package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.LoginCmd;
import com.bone.iam.domain.service.AuthService;
import com.bone.iam.domain.account.Account;
import com.bone.iam.infrastructure.security.JwtTokenService;
import com.bone.core.usecase.Capability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Capability(
    name = "Login",
    description = "账号登录",
    inputSchema = "{\"username\": \"string\", \"password\": \"string\"}",
    outputSchema = "{\"token\": \"string\", \"account\": {}}",
    idempotent = false,
    cost = 1,
    retryable = false,
    timeout = 5
)
@Component
@RequiredArgsConstructor
public class LoginHandler {
    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    public Map<String, Object> handle(LoginCmd cmd) {
        Account account = authService.authenticate(cmd.getUsername(), cmd.getPassword());
        if (account == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtTokenService.generateToken(account.getId(), account.getUsername().value());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("account", account);
        return result;
    }
}
