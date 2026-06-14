package com.bone.iam.application.event;

import com.bone.iam.domain.role.event.RoleCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleCreatedEventHandler {
  public void handle(RoleCreatedEvent event) {
    // 处理角色创建事件，例如记录审计日志等
    System.out.println("角色创建事件处理: " + event.getRoleName());
  }
}
