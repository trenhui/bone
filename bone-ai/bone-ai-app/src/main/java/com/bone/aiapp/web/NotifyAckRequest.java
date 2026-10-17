package com.bone.aiapp.web;

/** 消警 ack 回写请求（API 层 DTO）。 */
public record NotifyAckRequest(String alertId, Long tenantId, String ackedBy, String note) {}
