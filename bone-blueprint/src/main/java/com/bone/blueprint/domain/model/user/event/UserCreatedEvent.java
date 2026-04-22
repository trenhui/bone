package com.bone.blueprint.domain.model.user.event;

import com.bone.blueprint.domain.model.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 用户创建事件
 * <p>
 * 当用户创建成功时触发的领域事件
 * </p>
 */
@Getter
public class UserCreatedEvent extends ApplicationEvent {
    private final User user;
    private final LocalDateTime eventTime;
    
    public UserCreatedEvent(Object source, User user) {
        super(source);
        this.user = user;
        this.eventTime = LocalDateTime.now();
    }
}