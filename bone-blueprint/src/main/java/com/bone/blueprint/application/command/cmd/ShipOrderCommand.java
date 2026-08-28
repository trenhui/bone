package com.bone.blueprint.application.command.cmd;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 订单发货命令：仅已支付订单可发货（PAID → SHIPPED）。 */
@Getter
@RequiredArgsConstructor
public class ShipOrderCommand {

  private final Long orderId;
}
