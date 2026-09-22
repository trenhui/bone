package com.bone.iam.application.event;

import com.bone.iam.domain.model.audit.event.AuditLogCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 审计日志创建事件处理器（同步到外部存储等） */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogCreatedEventHandler {

  public void handle(AuditLogCreatedEvent event) {
    // 处理审计日志创建事件，例如同步到外部存储等
    log.info("审计日志创建事件处理: {}", event.getOperation());
  }
}
