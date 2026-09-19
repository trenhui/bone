package com.bone.blueprint.application.command;

/** 订单发货命令：仅已支付订单可发货（PAID → SHIPPED）。 */
public record ShipOrderCommand(Long orderId) {}
