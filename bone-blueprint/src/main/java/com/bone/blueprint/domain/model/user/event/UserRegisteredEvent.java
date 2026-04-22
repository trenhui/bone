package com.bone.blueprint.domain.model.user.event;

import com.bone.blueprint.domain.model.user.User;

/**
 * 用户注册事件
 * <p>
 * 当用户注册成功时触发
 * </p>
 */
public class UserRegisteredEvent {
    private final User user;

    public UserRegisteredEvent(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}