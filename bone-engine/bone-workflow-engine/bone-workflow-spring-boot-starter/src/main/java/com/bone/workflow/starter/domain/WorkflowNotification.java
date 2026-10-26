package com.bone.workflow.starter.domain;

import java.time.Instant;

/**
 * 站内信通知（领域值对象）。
 *
 * <p>与 {@code wf_notification} 行一一对应，是「查询收件箱」这一读能力的返回载体。写入侧由 {@code PersistenceNotificationPort}
 * 负责，读取侧由 {@link WorkflowInboxQuery} 暴露——读写分离，通知表的写由引擎在派单时触发， 读由办理人前端按需拉取，二者互不影响。
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
