package com.bone.iam.domain.account;

import com.bone.core.domain.AggregateRoot;
import com.bone.iam.domain.account.event.AccountCreatedEvent;
import com.bone.iam.domain.account.event.AccountDisabledEvent;
import com.bone.iam.domain.account.event.AccountEnabledEvent;
import com.bone.iam.domain.account.event.AccountLockedEvent;
import com.bone.iam.domain.account.event.PasswordChangedEvent;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.iam.domain.account.vo.Email;
import com.bone.iam.domain.account.vo.Username;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_account")
public class Account extends AggregateRoot<Long> {

    private Long id;
    private Long tenantId;
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

    public static Account create(Long id, Username username, String passwordHash, Email email,
                                  String phone, String realName, Long tenantId) {
        Account account = new Account();
        account.id = id;
        account.tenantId = tenantId;
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
        addDomainEvent(new AccountEnabledEvent(this.id));
    }

    public void disable() {
        if (this.status == AccountStatus.DISABLED) {
            throw new IllegalStateException("账户已处于禁用状态");
        }
        this.status = AccountStatus.DISABLED;
        this.updatedAt = LocalDateTime.now();
        addDomainEvent(new AccountDisabledEvent(this.id));
    }

    public void recordLoginSuccess(String ip) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ip;
        this.loginFailCount = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public void recordLoginFailure() {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) {
            this.status = AccountStatus.LOCKED;
            this.lockedAt = LocalDateTime.now().plusMinutes(30);
            addDomainEvent(new AccountLockedEvent(this.id, this.lockedAt));
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordUpdatedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        addDomainEvent(new PasswordChangedEvent(this.id));
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
