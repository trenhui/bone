package com.bone.iam.adapter.web.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bone.iam.adapter.web.dto.response.AccountDetailResp;
import com.bone.iam.adapter.web.dto.response.LoginResp;
import com.bone.iam.adapter.web.dto.response.RoleDetailResp;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.dto.RoleDetailDTO;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 时间字段 UTC 契约测试（i18n 方案 §6.4）：对外响应里的 {@code LocalDateTime} 必须在 converter 边界转为 {@link Instant}（UTC
 * 归一），序列化为带偏移的 ISO-8601（{@code ...Z}），否则前端 {@code dayjs.utc(v)} 会静默 +8h。
 *
 * <p>{@code MeController}（{@code GET /me}）使用与 {@link AuthWebConverter} 完全相同的 {@code
 * account.getXxx().toInstant(ZoneOffset.UTC)} 表达式，故此处对 domain {@link Account} 的覆盖同时守住该路径。
 */
class UtcTimeContractTest {

  private static final LocalDateTime SAMPLE = LocalDateTime.of(2026, 9, 25, 12, 0, 0);
  private static final Instant EXPECTED = Instant.parse("2026-09-25T12:00:00Z");

  @Test
  void accountDetailRespTimeIsUtcInstant() {
    AccountDTO dto = new AccountDTO();
    dto.setLastLoginAt(SAMPLE);
    dto.setCreatedAt(SAMPLE);
    dto.setUpdatedAt(SAMPLE);
    AccountDetailResp resp = new AccountWebConverter().toDetailResp(dto);
    assertEquals(EXPECTED, resp.getLastLoginAt());
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void roleDetailRespTimeIsUtcInstant() {
    RoleDetailDTO dto = new RoleDetailDTO();
    dto.setCreatedAt(SAMPLE);
    dto.setUpdatedAt(SAMPLE);
    RoleDetailResp resp = new RoleWebConverter().toDetailResp(dto);
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void loginRespTimeIsUtcInstant() {
    Account account =
        Account.create(
            1L,
            Username.of("alice"),
            "hash",
            Email.of("alice@bone.dev"),
            "13800000000",
            "Alice",
            100L);
    // lastLoginAt 在登录成功时才写入，这里触发一次以覆盖该字段的归一路径
    account.recordLoginSuccess("127.0.0.1");
    LoginResp resp =
        new AuthWebConverter()
            .toLoginResp(
                Map.of(
                    "account",
                    account,
                    "token",
                    "t",
                    "refreshToken",
                    "r",
                    "requirePasswordChange",
                    false,
                    "tenantId",
                    100L,
                    "tenantName",
                    "平台"));
    assertEquals(
        account.getLastLoginAt().toInstant(ZoneOffset.UTC), resp.getAccount().getLastLoginAt());
    assertEquals(
        account.getCreatedAt().toInstant(ZoneOffset.UTC), resp.getAccount().getCreatedAt());
  }
}
