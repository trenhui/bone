package com.bone.example.extension.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 支付结果领域对象 - 完整修复版
 */
public class PaymentResult {
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final String orderId;
    private final String userId;
    private final String transactionId;
    private final String status;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentMethod;
    private final LocalDateTime paymentTime;
    private final BigDecimal feeAmount;
    private final BigDecimal taxAmount;
    private final BigDecimal originalAmount;
    private final String errorCode;
    private final String errorMessage;
    private final String tenantCode;
    private final Integer pointsDeducted; // 新增字段
    private final Map<String, Object> extendedAttributes;

    private PaymentResult(Builder builder) {
        this.orderId = Objects.requireNonNull(builder.orderId, "orderId cannot be null");
        this.userId = Objects.requireNonNull(builder.userId, "userId cannot be null");
        this.transactionId = Objects.requireNonNull(builder.transactionId, "transactionId cannot be null");
        this.status = Objects.requireNonNull(builder.status, "status cannot be null");
        this.amount = Objects.requireNonNull(builder.amount, "amount cannot be null");
        this.currency = Objects.requireNonNull(builder.currency, "currency cannot be null");
        this.paymentMethod = Objects.requireNonNull(builder.paymentMethod, "paymentMethod cannot be null");
        this.paymentTime = Objects.requireNonNull(builder.paymentTime, "paymentTime cannot be null");
        this.feeAmount = builder.feeAmount != null ? builder.feeAmount : BigDecimal.ZERO;
        this.taxAmount = builder.taxAmount != null ? builder.taxAmount : BigDecimal.ZERO;
        this.originalAmount = builder.originalAmount != null ? builder.originalAmount : this.amount;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.tenantCode = Objects.requireNonNull(builder.tenantCode, "tenantCode cannot be null");
        this.pointsDeducted = builder.pointsDeducted != null ? builder.pointsDeducted : 0; // 默认值
        this.extendedAttributes = builder.extendedAttributes != null ?
                new HashMap<>(builder.extendedAttributes) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String orderId;
        private String userId;
        private String transactionId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private LocalDateTime paymentTime;
        private BigDecimal feeAmount;
        private BigDecimal taxAmount;
        private BigDecimal originalAmount;
        private String errorCode;
        private String errorMessage;
        private String tenantCode;
        private Integer pointsDeducted; // Builder 中新增
        private Map<String, Object> extendedAttributes = new HashMap<>();

        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder transactionId(String transactionId) { this.transactionId = transactionId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
        public Builder paymentTime(LocalDateTime paymentTime) { this.paymentTime = paymentTime; return this; }
        public Builder feeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; return this; }
        public Builder taxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; return this; }
        public Builder originalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder tenantCode(String tenantCode) { this.tenantCode = tenantCode; return this; }
        public Builder pointsDeducted(Integer pointsDeducted) { this.pointsDeducted = pointsDeducted; return this; } // 新增方法
        public Builder extendedAttribute(String key, Object value) {
            this.extendedAttributes.put(key, value); return this;
        }
        public Builder extendedAttributes(Map<String, Object> extendedAttributes) {
            this.extendedAttributes = extendedAttributes; return this;
        }

        public PaymentResult build() {
            return new PaymentResult(this);
        }
    }

    // ==================== 业务方法 ====================
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    public BigDecimal getTotalCost() {
        return amount.add(feeAmount).add(taxAmount);
    }

    public Object getExtendedAttribute(String key) {
        return extendedAttributes.get(key);
    }

    // ==================== Getter 方法 ====================
    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getTransactionId() { return transactionId; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public LocalDateTime getPaymentTime() { return paymentTime; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getTenantCode() { return tenantCode; }
    public Integer getPointsDeducted() { return pointsDeducted; } // 新增 Getter
    public Map<String, Object> getExtendedAttributes() {
        return new HashMap<>(extendedAttributes);
    }

    @Override
    public String toString() {
        return "PaymentResult{" +
                "orderId='" + orderId + '\'' +
                ", transactionId='" + transactionId + '\'' +
                ", status='" + status + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", pointsDeducted=" + pointsDeducted +
                '}';
    }
}