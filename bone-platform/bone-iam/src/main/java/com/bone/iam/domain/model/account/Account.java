package com.bone.iam.domain.model.account;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.iam.domain.model.account.event.AccountCreatedEvent;
import com.bone.iam.domain.model.account.event.AccountDisabledEvent;
import com.bone.iam.domain.model.account.event.AccountEnabledEvent;
import com.bone.iam.domain.model.account.event.AccountLockedEvent;
import com.bone.iam.domain.model.account.event.PasswordChangedEvent;
import com.bone.iam.domain.model.account.vo.AccountStatus;
import com.bone.iam.domain.model.account.vo.Email;
import com.bone.iam.domain.model.account.vo.Username;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_account")
/** 多租户账户聚合根样板：{@link TenantAggregateRoot} + IAM 域内 {@link LocalDateTime} 审计字段。 */
public class Account extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  /**
   * 子类重新声明了 id 字段（@Getter 不会生成 setter），若缺省则 setId() 继承自父类仅设置 {@code Entity.id}，导致 create() 后
   * getId() 返回 null（字段遮蔽）。此处显式 override 到子类字段。
   */
  @Override
  public void setId(Long id) {
    this.id = id;
  }

  private Username username;
  private String passwordHash;
  private Email email;
  private String phone;
  private String realName;
  private String avatarUrl;
  private AccountStatus status;
  private boolean isAdmin;
  private LocalDateTime lastLoginAt;
  private String lastLoginIp;
  private int loginFailCount;
  private LocalDateTime lockedAt;
  private LocalDateTime passwordUpdatedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Account create(
      Long id,
      Username username,
      String passwordHash,
      Email email,
      String phone,
      String realName,
      Long tenantId) {
    Account account = new Account();
    account.setId(id);
    account.setTenantId(tenantId);
    account.username = username;
    account.passwordHash = passwordHash;
    account.email = email;
    account.phone = phone;
    account.realName = realName;
    account.status = AccountStatus.ENABLED;
    account.isAdmin = false;
    account.loginFailCount = 0;
    account.createdAt = LocalDateTime.now();
    account.updatedAt = LocalDateTime.now();
    account.passwordUpdatedAt = LocalDateTime.now();
    account.addDomainEvent(new AccountCreatedEvent(account));
    return account;
  }

  public void enable() {
    if (this.status == AccountStatus.ENABLED) {
      throw new IllegalStateException("账户已处于启用状态");
    }
    this.status = AccountStatus.ENABLED;
    this.loginFailCount = 0;
    this.lockedAt = null;
    this.updatedAt = LocalDateTime.now();
    addDomainEvent(new AccountEnabledEvent(getId()));
  }

  public void disable() {
    if (this.status == AccountStatus.DISABLED) {
      throw new IllegalStateException("账户已处于禁用状态");
    }
    this.status = AccountStatus.DISABLED;
    this.updatedAt = LocalDateTime.now();
    addDomainEvent(new AccountDisabledEvent(getId()));
  }

  public void recordLoginSuccess(String ip) {
    this.lastLoginAt = LocalDateTime.now();
    this.lastLoginIp = ip;
    this.loginFailCount = 0;
    this.updatedAt = LocalDateTime.now();
  }

  /** 默认阈值：5 次失败 → 锁定 30 分钟（沿用历史调用方）。 */
  public void recordLoginFailure() {
    recordLoginFailure(5, 30);
  }

  /**
   * 失败计数 +1；累计达到 {@code threshold} 时进入 LOCKED 状态，{@code lockedAt} 设为当前时间 + {@code lockMinutes}。
   *
   * @param threshold 锁定阈值（&lt;= 0 表示不锁定，仅计数）
   * @param lockMinutes 锁定持续分钟数
   */
  public void recordLoginFailure(int threshold, int lockMinutes) {
    this.loginFailCount++;
    if (threshold > 0 && this.loginFailCount >= threshold) {
      this.status = AccountStatus.LOCKED;
      this.lockedAt = LocalDateTime.now().plusMinutes(Math.max(1, lockMinutes));
      addDomainEvent(new AccountLockedEvent(getId(), this.lockedAt));
    }
    this.updatedAt = LocalDateTime.now();
  }

  public void updatePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.passwordUpdatedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    addDomainEvent(new PasswordChangedEvent(getId()));
  }

  public void updateProfile(String realName, String phone, String avatarUrl) {
    this.realName = realName;
    this.phone = phone;
    if (avatarUrl != null) {
      this.avatarUrl = avatarUrl;
    }
    this.updatedAt = LocalDateTime.now();
  }

  public boolean isLocked() {
    if (this.status != AccountStatus.LOCKED) {
      return false;
    }
    if (this.lockedAt != null && this.lockedAt.isBefore(LocalDateTime.now())) {
      this.status = AccountStatus.ENABLED;
      this.loginFailCount = 0;
      this.lockedAt = null;
      return false;
    }
    return true;
  }
}
