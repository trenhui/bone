package com.bone.blueprint.application.command.cmd;

/** 关闭超时未支付支付单命令（由定时任务扫描后逐笔下发）。 */
public record CloseExpiredPaymentCommand(Long paymentId) {}
