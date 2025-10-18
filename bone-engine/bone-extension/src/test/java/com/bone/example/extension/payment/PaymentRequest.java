package com.bone.example.extension.payment;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付请求类
 */
@Data
public class PaymentRequest {
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String couponId;
    private int pointsToDeduct;
    private String paymentMethod;
}