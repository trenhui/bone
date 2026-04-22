package com.bone.blueprint.domain.model.user;

import com.bone.blueprint.domain.model.user.event.UserRegisteredEvent;
import com.bone.blueprint.domain.model.user.vo.Password;
import com.bone.blueprint.domain.model.user.vo.UserId;
import com.bone.blueprint.domain.model.user.vo.Username;
import com.bone.blueprint.domain.model.user.vo.UserStatus;
import com.bone.core.domain.entity.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户聚合根
 * <p>
 * 领域模型，代表系统中的用户概念，作为聚合根
 * </p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends AggregateRoot<UserId> {
    /**
     * 用户名
     */
    private Username username;
    
    /**
     * 密码
     */
    private Password password;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 状态
     */
    private UserStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 注册用户
     */
    public static User register(Username username, Password password, String nickname) {
        User user = new User();
        user.setId(UserId.generate());
        user.username = username;
        user.password = password;
        user.nickname = nickname;
        user.status = UserStatus.ENABLED;
        user.createTime = LocalDateTime.now();
        user.updateTime = LocalDateTime.now();
        user.addDomainEvent(new UserRegisteredEvent(user));
        return user;
    }
    
    /**
     * 禁用用户
     */
    public void disable() {
        this.status = UserStatus.DISABLED;
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 启用用户
     */
    public void enable() {
        this.status = UserStatus.ENABLED;
        this.updateTime = LocalDateTime.now();
    }
    
    /**
     * 更新用户信息
     */
    public void update(String nickname) {
        this.nickname = nickname;
        this.updateTime = LocalDateTime.now();
    }
}