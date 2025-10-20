package com.bone.example.extension.payment;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付计算结果类
 */
@Data
@Builder
public class PaymentCalculationResult {
    private BigDecimal originalAmount;
    private BigDecimal finalAmount;
    private Map<String, BigDecimal> deductionDetails;
    private BigDecimal feeAmount;
    private BigDecimal taxAmount;
    private String currency;
}