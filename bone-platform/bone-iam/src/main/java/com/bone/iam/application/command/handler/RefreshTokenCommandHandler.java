package com.bone.iam.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.command.cmd.RefreshTokenCommand;
import com.bone.iam.application.query.handler.AccountAuthoritiesQueryHandler;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.repository.AccountRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RefreshTokenCommandHandler {

    private final RefreshTokenIssuer refreshTokenIssuer;
    private final AccountRepository accountRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AccountAuthoritiesQueryHandler accountAuthoritiesQueryHandler;

    @Transactional
    public Map<String, String> handle(RefreshTokenCommand cmd) {
        Map<String, String> rotated = refreshTokenIssuer.rotate(cmd.getRefreshToken());
        long accountId = Long.parseLong(rotated.get("accountId"));
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            throw NotFoundException.of("账户不存在或已禁用");
        }
        List<String> scopes =
                accountAuthoritiesQueryHandler.resolvePermissionCodes(account.getId(), account.isAdmin());
        String accessToken = accessTokenIssuer.issueAccessToken(
                account.getId(), account.getUsername().value(), account.getTenantId(), scopes);
        return Map.of(
                "accessToken", accessToken,
                "refreshToken", rotated.get("refreshToken"));
    }
}
