package com.bone.blueprint.application.query.qry;

/**
 * 订单详情查询对象（不可变 record，与 {@link PaymentDetailQuery} 保持一致风格）。
 *
 * @param orderId 订单 ID
 */
public record OrderDetailQuery(Long orderId) {}
