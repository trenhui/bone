package com.bone.blueprint.infrastructure.messaging.outbox;

/** Outbox 投递状态（<strong>技术状态机</strong>，非业务状态）。 */
public enum OutboxStatus {
  PENDING,
  SENT,
  FAILED
}
