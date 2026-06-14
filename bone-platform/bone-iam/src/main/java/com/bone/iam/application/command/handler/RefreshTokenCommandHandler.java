package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.command.cmd.RefreshTokenCommand;
import com.bone.iam.application.query.handler.AccountAuthoritiesQueryHandler;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.repository.AccountRepository;
import java.util.HashMap;
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
    if (cmd == null || cmd.getRefreshToken() == null || cmd.getRefreshToken().isBlank()) {
      throw BizException.of(400, "刷新令牌不能为空");
    }
    Map<String, String> rotated;
    try {
      rotated = refreshTokenIssuer.rotate(cmd.getRefreshToken());
    } catch (IllegalArgumentException ex) {
      throw BizException.of(401, ex.getMessage());
    }
    long accountId = Long.parseLong(rotated.get("accountId"));
    Account account = accountRepository.findById(accountId);
    if (account == null) {
      throw NotFoundException.of("账户不存在或已禁用");
    }
    List<String> scopes =
        accountAuthoritiesQueryHandler.resolvePermissionCodes(account.getId(), account.isAdmin());
    String accessToken =
        accessTokenIssuer.issueAccessToken(
            account.getId(), account.getUsername().value(), account.getTenantId(), scopes);
    String newRefreshToken = rotated.get("refreshToken");
    if (newRefreshToken == null) {
      throw BizException.of(500, "刷新令牌轮换失败：未返回新的刷新令牌");
    }
    Map<String, String> result = new HashMap<>();
    result.put("accessToken", accessToken);
    result.put("refreshToken", newRefreshToken);
    return result;
  }
}
