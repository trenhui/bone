package com.bone.blueprint.application.command;

import java.math.BigDecimal;

/**
 * 支付退款命令：对已成功支付单发起退款。
 *
 * @param paymentId 支付单 ID
 * @param refundAmount 退款金额（≤ 已付金额，支持部分退款）
 */
public record RefundPaymentCommand(Long paymentId, BigDecimal refundAmount) {}
