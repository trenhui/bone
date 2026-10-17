package com.bone.aiapp.web;

/**
 * 消警 ack 回写请求（API 层 DTO）。
 *
 * <p>不含 tenantId——租户一律从上下文取（详设 §3.1），禁入参传租户。
 */
public record NotifyAckRequest(String alertId, String ackedBy, String note) {}
