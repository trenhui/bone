package com.bone.iam.application.event;

import com.bone.iam.domain.model.user.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCreatedHandler {
    public void handle(UserCreatedEvent event) {
        // 处理用户创建事件，例如发送欢迎邮件、记录审计日志等
        System.out.println("用户创建事件处理: " + event.username());
    }
}