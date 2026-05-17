package com.bone.integration.domain.outbox;

/** Outbox 投递状态（与 int_outbox.status 一致）。 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
