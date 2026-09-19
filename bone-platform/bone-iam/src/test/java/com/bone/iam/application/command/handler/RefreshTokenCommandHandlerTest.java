package com.bone.iam.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.RefreshTokenCommand;
import com.bone.iam.application.query.handler.AccountAuthoritiesQueryHandler;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.Email;
import com.bone.iam.domain.account.vo.Username;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.repository.AccountRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCommandHandlerTest {

  @Mock RefreshTokenIssuer refreshTokenIssuer;
  @Mock AccountRepository accountRepository;
  @Mock AccessTokenIssuer accessTokenIssuer;
  @Mock AccountAuthoritiesQueryHandler accountAuthoritiesQueryHandler;

  RefreshTokenCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new RefreshTokenCommandHandler(
            refreshTokenIssuer,
            accountRepository,
            accessTokenIssuer,
            accountAuthoritiesQueryHandler);
  }

  @Test
  void refreshTokenSuccessfully() {
    Account account =
        Account.create(1L, Username.of("bob"), "hash", Email.of("b@b.com"), null, null, 0L);

    when(refreshTokenIssuer.rotate("old-refresh"))
        .thenReturn(Map.of("accountId", "1", "tenantId", "0", "refreshToken", "new-refresh"));
    when(accountRepository.findById(1L)).thenReturn(account);
    when(accountAuthoritiesQueryHandler.resolvePermissionCodes(1L, false))
        .thenReturn(List.of("iam:read"));
    when(accessTokenIssuer.issueAccessToken(1L, "bob", 0L, List.of("iam:read")))
        .thenReturn("new-access");

    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("old-refresh");

    Map<String, String> result = handler.handle(cmd);

    assertThat(result.get("accessToken")).isEqualTo("new-access");
    assertThat(result.get("refreshToken")).isEqualTo("new-refresh");
    verify(refreshTokenIssuer, times(1)).rotate("old-refresh");
  }

  @Test
  void blankRefreshTokenThrows400() {
    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("  ");

    assertThatThrownBy(() -> handler.handle(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("刷新令牌不能为空");
  }

  @Test
  void invalidRefreshTokenThrows401() {
    when(refreshTokenIssuer.rotate("bad")).thenThrow(new IllegalArgumentException("invalid token"));

    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("bad");

    assertThatThrownBy(() -> handler.handle(cmd)).isInstanceOf(BizException.class);
  }
}
