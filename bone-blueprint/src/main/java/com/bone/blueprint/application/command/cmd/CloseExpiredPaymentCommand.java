package com.bone.blueprint.application.command.cmd;

/**
 * 关闭超时未支付支付单命令（由定时任务扫描后逐笔下发）。
 *
 * @param paymentId 支付单 ID
 * @param tenantId 租户 ID：定时任务为异步入口，必须显式携带（E-4.4），否则 Handler 会落到降级后的平台租户。
 */
public record CloseExpiredPaymentCommand(Long paymentId, Long tenantId) {}
