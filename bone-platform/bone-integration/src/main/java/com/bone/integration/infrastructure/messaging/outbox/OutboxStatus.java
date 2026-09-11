package com.bone.integration.infrastructure.messaging.outbox;

/**
 * Outbox 投递状态（与 int_outbox.status 一致）。
 *
 * <p>Outbox 是消息投递的<strong>技术设施</strong>，其状态机（PENDING/SENT/FAILED）无业务不变量，故随记录 一并放在基础设施层，不占用 domain
 * 包。
 */
public enum OutboxStatus {
  PENDING,
  SENT,
  FAILED
}
