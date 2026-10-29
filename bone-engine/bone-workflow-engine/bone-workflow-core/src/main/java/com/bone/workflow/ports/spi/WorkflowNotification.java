package com.bone.workflow.ports.spi;

import java.time.Instant;

/**
 * 站内信通知（值对象）。
 *
 * <p>与 {@code wf_notification} 行一一对应，是「查询收件箱」这一读能力的返回载体。写入侧由 {@link NotificationPort} 的持久化实现负责，读取侧由
 * {@link WorkflowInboxQuery} 暴露——读写分离，通知表的写由引擎在派单时触发， 读由办理人前端按需拉取，二者互不影响。
 *
 * <p>居于 core 端口层而非 starter：宿主的入站面（REST Controller）要返回它，而宿主不依赖 starter 的内部包—— 读端口与值对象必须和 {@link
 * NotificationPort} 同层，否则「写得出、查不到」。
 */
public record WorkflowNotification(
    String id,
    long tenantId,
    String recipientId,
    String type,
    String instanceId,
    String entityCode,
    String recordId,
    String nodeCode,
    String title,
    String body,
    boolean read,
    Instant createdAt) {}
