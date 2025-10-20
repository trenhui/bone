package com.bone.example.extension.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交易请求实现类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequestImpl implements TransactionRequest {
    private String transactionId;
    private String userId;
    private double amount;
    private String transactionType;
    private String timestamp;
    private String location;
    private String deviceInfo;
    private String ipAddress;
    // 额外字段
    private String paymentMethod;
    private String merchantId;
    private String productCategory;
}