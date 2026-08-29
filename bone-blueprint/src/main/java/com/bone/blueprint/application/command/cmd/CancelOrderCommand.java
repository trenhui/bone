package com.bone.blueprint.application.command.cmd;

/** 订单取消命令（不可变 record，样板统一命令/查询用 record 的写法）。 */
public record CancelOrderCommand(Long orderId) {}
