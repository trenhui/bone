package com.bone.example.extension.payment;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付结果类
 */
@Data
public class PaymentResult {
    private String transactionId;
    private boolean success;
    private String errorCode;
    private String errorMessage;
    private BigDecimal finalAmount;
    private String userId;
    private int pointsDeducted;
}