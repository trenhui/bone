package com.bone.blueprint.application.command.cmd;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 订单送达确认命令：仅已发货订单可确认送达（SHIPPED → DELIVERED）。 */
@Getter
@RequiredArgsConstructor
public class DeliverOrderCommand {

  private final Long orderId;
}
