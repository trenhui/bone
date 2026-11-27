package com.bone.procurement.contract.domain.model.review;

/** 待投递 Outbox 消息：payload 为严格 7 字段事件，delivered 标识是否已被显式驱动投递。 */
public record ContractReviewedOutboxMessage(
    String tenantId, String reviewId, ContractReviewedEvent event, boolean delivered) {}
