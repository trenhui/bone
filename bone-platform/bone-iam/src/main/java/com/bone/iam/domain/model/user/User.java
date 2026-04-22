package com.bone.iam.domain.model.user;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.iam.domain.model.user.event.UserCreatedEvent;
import com.bone.iam.domain.model.user.vo.*;
import com.bone.core.util.DistributedIdGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends AggregateRoot<UserId> {
    private UserId id;
    private Long dbId;
    private Username username;
    private String passwordHash;
    private Email email;
    private UserStatus status;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static User register(Username username, String passwordHash, Email email, Long tenantId) {
        User user = new User();
        user.id = UserId.of(DistributedIdGenerator.generateUuid());
        user.username = username;
        user.passwordHash = passwordHash;
        user.email = email;
        user.status = UserStatus.ENABLED;
        user.tenantId = tenantId;
        user.createTime = LocalDateTime.now();
        user.updateTime = LocalDateTime.now();
        user.addDomainEvent(new UserCreatedEvent(user));
        return user;
    }

    public void disable() {
        if (this.status == UserStatus.DISABLED) {
            throw new DomainException("用户已处于禁用状态");
        }
        this.status = UserStatus.DISABLED;
        this.updateTime = LocalDateTime.now();
    }

    public void enable() {
        if (this.status == UserStatus.ENABLED) {
            throw new DomainException("用户已处于启用状态");
        }
        this.status = UserStatus.ENABLED;
        this.updateTime = LocalDateTime.now();
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updateTime = LocalDateTime.now();
    }

    public void updateEmail(Email email) {
        this.email = email;
        this.updateTime = LocalDateTime.now();
    }

    void setDbId(Long dbId) {
        this.dbId = dbId;
    }
}