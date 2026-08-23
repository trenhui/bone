package com.bone.iam.application.event;

import com.bone.iam.domain.role.event.RoleCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 角色创建事件处理器（审计日志等） */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoleCreatedEventHandler {

  public void handle(RoleCreatedEvent event) {
    // 处理角色创建事件，例如记录审计日志等
    log.info("角色创建事件处理: {}", event.getRoleName());
  }
}
