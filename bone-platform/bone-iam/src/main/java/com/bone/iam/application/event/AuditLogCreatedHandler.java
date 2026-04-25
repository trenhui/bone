package com.bone.iam.application.event;

import com.bone.iam.domain.audit.event.AuditLogCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogCreatedHandler {
    public void handle(AuditLogCreatedEvent event) {
        // 处理审计日志创建事件，例如同步到外部存储等
        System.out.println("审计日志创建事件处理: " + event.getOperation());
    }
}