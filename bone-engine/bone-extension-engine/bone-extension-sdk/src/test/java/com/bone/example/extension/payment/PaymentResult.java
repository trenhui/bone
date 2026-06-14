package com.bone.example.extension.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** PaymentResult - 终极修复版（解决所有 NPE 问题） 使用手写 Builder 模式，完全避免 Lombok 问题 */
public final class PaymentResult {

  private static final String SUCCESS = "SUCCESS";
  private static final String FAILED = "FAILED";

  // 必填字段
  private final String orderId;
  private final String userId;
  private final String transactionId;
  private final String status;
  private final BigDecimal amount;
  private final String currency;
  private final LocalDateTime paymentTime;
  private final String tenantCode;

  // 可选字段
  private final String paymentMethod;
  private final BigDecimal feeAmount;
  private final BigDecimal taxAmount;
  private final BigDecimal originalAmount;
  private final String errorCode;
  private final String errorMessage;
  private final Integer pointsDeducted;
  private final Map<String, Object> extendedAttributes;

  // 在 PaymentResult 的构造函数中添加默认值
  private PaymentResult(Builder builder) {
    this.orderId = Objects.requireNonNull(builder.orderId, "orderId cannot be null");
    this.userId = Objects.requireNonNull(builder.userId, "userId cannot be null");
    this.transactionId =
        Objects.requireNonNull(builder.transactionId, "transactionId cannot be null");
    this.status = Objects.requireNonNull(builder.status, "status cannot be null");
    this.amount = Objects.requireNonNull(builder.amount, "amount cannot be null");

    // 修复：为所有必填字段提供默认值
    this.tenantCode = builder.tenantCode != null ? builder.tenantCode : "UNKNOWN_TENANT";
    this.currency = builder.currency != null ? builder.currency : "CNY";
    this.paymentTime = builder.paymentTime != null ? builder.paymentTime : LocalDateTime.now();

    // 关键修复：paymentMethod 使用 builder 中的值或默认值
    this.paymentMethod = builder.paymentMethod != null ? builder.paymentMethod : "UNKNOWN";
    this.feeAmount = builder.feeAmount != null ? builder.feeAmount : BigDecimal.ZERO;
    this.taxAmount = builder.taxAmount != null ? builder.taxAmount : BigDecimal.ZERO;
    this.originalAmount = builder.originalAmount != null ? builder.originalAmount : this.amount;
    this.errorCode = builder.errorCode;
    this.errorMessage = builder.errorMessage;
    this.pointsDeducted = builder.pointsDeducted != null ? builder.pointsDeducted : 0;
    this.extendedAttributes =
        builder.extendedAttributes != null ? builder.extendedAttributes : new HashMap<>();
  }

  // ==================== Builder 模式 ====================

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
    private LocalDateTime paymentTime;
    private String tenantCode;
    private String paymentMethod;
    private BigDecimal feeAmount;
    private BigDecimal taxAmount;
    private BigDecimal originalAmount;
    private String errorCode;
    private String errorMessage;
    private Integer pointsDeducted;
    private Map<String, Object> extendedAttributes;

    private Builder() {}

    public Builder orderId(String orderId) {
      this.orderId = orderId;
      return this;
    }

    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder transactionId(String transactionId) {
      this.transactionId = transactionId;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder paymentTime(LocalDateTime paymentTime) {
      this.paymentTime = paymentTime;
      return this;
    }

    public Builder tenantCode(String tenantCode) {
      this.tenantCode = tenantCode;
      return this;
    }

    public Builder paymentMethod(String paymentMethod) {
      this.paymentMethod = paymentMethod;
      return this;
    }

    public Builder feeAmount(BigDecimal feeAmount) {
      this.feeAmount = feeAmount;
      return this;
    }

    public Builder taxAmount(BigDecimal taxAmount) {
      this.taxAmount = taxAmount;
      return this;
    }

    public Builder originalAmount(BigDecimal originalAmount) {
      this.originalAmount = originalAmount;
      return this;
    }

    public Builder errorCode(String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    public Builder errorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public Builder pointsDeducted(Integer pointsDeducted) {
      this.pointsDeducted = pointsDeducted;
      return this;
    }

    public Builder extendedAttributes(Map<String, Object> extendedAttributes) {
      this.extendedAttributes = extendedAttributes;
      return this;
    }

    public PaymentResult build() {
      return new PaymentResult(this);
    }
  }

  // ==================== Getter 方法 ====================

  public String getOrderId() {
    return orderId;
  }

  public String getUserId() {
    return userId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public String getStatus() {
    return status;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public LocalDateTime getPaymentTime() {
    return paymentTime;
  }

  public String getTenantCode() {
    return tenantCode;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public BigDecimal getFeeAmount() {
    return feeAmount;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getOriginalAmount() {
    return originalAmount;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Integer getPointsDeducted() {
    return pointsDeducted;
  }

  public Map<String, Object> getExtendedAttributes() {
    return extendedAttributes;
  }

  // ==================== 业务方法 ====================

  public boolean isSuccess() {
    return SUCCESS.equals(status);
  }

  public boolean isFailed() {
    return FAILED.equals(status);
  }

  public BigDecimal getTotalCost() {
    return amount.add(feeAmount).add(taxAmount);
  }

  public Object getExtendedAttribute(String key) {
    return extendedAttributes != null ? extendedAttributes.get(key) : null;
  }
}
