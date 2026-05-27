package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.LoginCommand;
import com.bone.iam.domain.service.AuthService;
import com.bone.iam.domain.account.Account;
import com.bone.iam.application.query.handler.AccountAuthoritiesQueryHandler;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import java.util.List;
import com.bone.core.capability.Capability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
public class LoginCommandHandler {
    private final AuthService authService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final AccountAuthoritiesQueryHandler accountAuthoritiesQueryHandler;

    @Transactional
    public Map<String, Object> handle(LoginCommand cmd) {
        Account account = authService.authenticate(cmd.getUsername(), cmd.getPassword());
        if (account == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        List<String> scopes = accountAuthoritiesQueryHandler.resolvePermissionCodes(
                account.getId(), account.isAdmin());
        String token = accessTokenIssuer.issueAccessToken(
                account.getId(), account.getUsername().value(), account.getTenantId(), scopes);
        String refreshToken = refreshTokenIssuer.issue(account.getId(), account.getTenantId());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("account", account);
        return result;
    }
}
