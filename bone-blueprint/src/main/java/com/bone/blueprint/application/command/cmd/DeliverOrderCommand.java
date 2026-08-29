package com.bone.blueprint.application.command.cmd;

/** 订单送达确认命令：仅已发货订单可确认送达（SHIPPED → DELIVERED）。 */
public record DeliverOrderCommand(Long orderId) {}
