package com.bone.example.extension.payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 支付计算结果领域对象 - 精炼实现
 * <p>
 * 支持 Builder 模式构建，确保对象不可变性。
 * 包含支付金额计算相关明细。
 *
 * <h3>设计原则：</h3>
 * <ul>
 * <li>不可变性：字段 final，通过 Builder 构建</li>
 * <li>完整性：包含所有计算相关信息</li>
 * <li>业务方法：如 getDiscountAmount() 计算折扣</li>
 * <li>扩展性：extendedAttributes 支持自定义属性</li>
 * <li>安全性：字段非null校验，金额默认零值</li>
 * </ul>
 */
public class PaymentCalculationResult {

    private final BigDecimal originalAmount;
    private final BigDecimal finalAmount;
    private final BigDecimal feeAmount;
    private final BigDecimal taxAmount;
    private final String currency;
    private final Map<String, Object> extendedAttributes;

    // ==================== Builder 模式 ====================

    private PaymentCalculationResult(Builder builder) {
        this.originalAmount = Objects.requireNonNull(builder.originalAmount, "originalAmount cannot be null");
        this.finalAmount = Objects.requireNonNull(builder.finalAmount, "finalAmount cannot be null");
        this.feeAmount = builder.feeAmount != null ? builder.feeAmount : BigDecimal.ZERO;
        this.taxAmount = builder.taxAmount != null ? builder.taxAmount : BigDecimal.ZERO;
        this.currency = Objects.requireNonNull(builder.currency, "currency cannot be null");
        this.extendedAttributes = builder.extendedAttributes != null ? new HashMap<>(builder.extendedAttributes) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BigDecimal originalAmount;
        private BigDecimal finalAmount;
        private BigDecimal feeAmount;
        private BigDecimal taxAmount;
        private String currency;
        private Map<String, Object> extendedAttributes = new HashMap<>();

        public Builder originalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; return this; }
        public Builder finalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; return this; }
        public Builder feeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; return this; }
        public Builder taxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder extendedAttribute(String key, Object value) { this.extendedAttributes.put(key, value); return this; }
        public Builder extendedAttributes(Map<String, Object> extendedAttributes) { this.extendedAttributes = extendedAttributes; return this; }

        public PaymentCalculationResult build() {
            return new PaymentCalculationResult(this);
        }
    }

    // ==================== 业务方法 ====================

    /**
     * 计算折扣金额（原始金额 - 最终金额）
     */
    public BigDecimal getDiscountAmount() {
        return originalAmount.subtract(finalAmount);
    }

    /**
     * 计算总费用（最终金额 + 手续费 + 税费）
     */
    public BigDecimal getTotalCost() {
        return finalAmount.add(feeAmount).add(taxAmount);
    }

    /**
     * 获取扩展属性值
     * @param key 属性键
     * @return 属性值，或 null
     */
    public Object getExtendedAttribute(String key) {
        return extendedAttributes.get(key);
    }

    // ==================== Getter 方法 ====================

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public String getCurrency() { return currency; }
    public Map<String, Object> getExtendedAttributes() { return new HashMap<>(extendedAttributes); }

    @Override
    public String toString() {
        return "PaymentCalculationResult{" +
                "originalAmount=" + originalAmount +
                ", finalAmount=" + finalAmount +
                ", feeAmount=" + feeAmount +
                ", taxAmount=" + taxAmount +
                ", currency='" + currency + '\'' +
                '}';
    }
}