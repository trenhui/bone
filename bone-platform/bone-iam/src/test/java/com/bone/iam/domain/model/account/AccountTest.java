package com.bone.iam.domain.model.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import com.bone.iam.domain.model.account.valueobject.AccountStatus;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import org.junit.jupiter.api.Test;

/** {@link Account} 纯单测：启用/禁用防重、登录锁定阈值与凭据/资料更新（无容器）。 */
class AccountTest {

  private Account createAccount() {
    return Account.create(
        1L,
        Username.of("alice"),
        "hash-1",
        Email.of("alice@bone.dev"),
        "13800000000",
        "Alice",
        100L);
  }

  @Test
  void testCreateDefaultsToEnabledNonAdmin() {
    Account account = createAccount();

    assertEquals(AccountStatus.ENABLED, account.getStatus());
    assertEquals(1L, account.getId());
    assertEquals(100L, account.getTenantId());
    assertFalse(account.isAdmin());
    assertEquals(0, account.getLoginFailCount());
  }

  @Test
  void testEnableDisableGuards() {
    Account account = createAccount();

    // 新建即 ENABLED，重复 enable 拒绝
    assertThrows(IllegalStateException.class, account::enable);

    account.disable();
    assertEquals(AccountStatus.DISABLED, account.getStatus());
    // 重复 disable 拒绝
    assertThrows(IllegalStateException.class, account::disable);

    account.enable();
    assertEquals(AccountStatus.ENABLED, account.getStatus());
  }

  @Test
  void testLoginFailuresLockAtThreshold() {
    Account account = createAccount();

    account.recordLoginFailure(2, 30);
    assertEquals(1, account.getLoginFailCount());
    assertEquals(AccountStatus.ENABLED, account.getStatus());
    assertFalse(account.isLocked());

    account.recordLoginFailure(2, 30);
    assertEquals(2, account.getLoginFailCount());
    assertEquals(AccountStatus.LOCKED, account.getStatus());
    assertTrue(account.isLocked());
    assertNotNull(account.getLockedAt());
  }

  @Test
  void testRecordLoginSuccessClearsFailureCounter() {
    Account account = createAccount();
    for (int i = 0; i < 3; i++) {
      account.recordLoginFailure(5, 30);
    }

    account.recordLoginSuccess("192.168.1.1");

    assertEquals(0, account.getLoginFailCount());
    assertEquals("192.168.1.1", account.getLastLoginIp());
    assertNotNull(account.getLastLoginAt());
  }

  @Test
  void testUpdatePasswordUpdatesHash() {
    Account account = createAccount();

    account.updatePassword("hash-2");

    assertEquals("hash-2", account.getPasswordHash());
    assertNotNull(account.getPasswordUpdatedAt());
  }

  @Test
  void testUpdateProfileKeepsAvatarWhenOmitted() {
    Account account = createAccount();

    account.updateProfile("Alice Ren", "13900000000", null);
    assertEquals("Alice Ren", account.getRealName());
    assertNull(account.getAvatarUrl());

    account.updateProfile("Alice Ren2", null, "/avatar/alice.png");
    assertEquals("Alice Ren2", account.getRealName());
    assertEquals("/avatar/alice.png", account.getAvatarUrl());
  }

  @Test
  void testChangeDeptSetsClearsAndIsIdempotent() {
    Account account = createAccount();
    assertNull(account.getDeptId());

    account.changeDept(88L);
    assertEquals(88L, account.getDeptId());
    java.time.LocalDateTime afterSet = account.getUpdatedAt();

    // 幂等：同值再设不推进更新时间（避免无意义的乐观锁版本号增长）
    account.changeDept(88L);
    assertEquals(afterSet, account.getUpdatedAt());

    // 撤销归属
    account.changeDept(null);
    assertNull(account.getDeptId());
  }

  @Test
  void testValueObjectsRejectInvalidInput() {
    assertThrows(DomainException.class, () -> Username.of(" "));
    assertThrows(DomainException.class, () -> Username.of("ab"));
    assertThrows(DomainException.class, () -> Email.of("not-an-email"));
  }
}
