package com.bone.example.extension.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * 支付计算结果类
 * <p>
 * 封装支付金额计算的详细结果，包括原始金额、最终金额、折扣明细、手续费、税费及货币类型等信息
 */
public class PaymentCalculationResult {
    /** 原始金额 */
    private BigDecimal originalAmount;
    /** 最终支付金额 */
    private BigDecimal finalAmount;
    /** 折扣明细，键为折扣类型，值为折扣金额 */
    private Map<String, BigDecimal> deductionDetails;
    /** 手续费金额 */
    private BigDecimal feeAmount;
    /** 税费金额 */
    private BigDecimal taxAmount;
    /** 货币类型代码 */
    private String currency;
    
    @Override
    public String toString() {
        return "PaymentCalculationResult{" +
                "originalAmount=" + originalAmount +
                ", finalAmount=" + finalAmount +
                ", deductionDetails=" + deductionDetails +
                ", feeAmount=" + feeAmount +
                ", taxAmount=" + taxAmount +
                ", currency='" + currency + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentCalculationResult that = (PaymentCalculationResult) o;
        return Objects.equals(originalAmount, that.originalAmount) &&
               Objects.equals(finalAmount, that.finalAmount) &&
               Objects.equals(deductionDetails, that.deductionDetails) &&
               Objects.equals(feeAmount, that.feeAmount) &&
               Objects.equals(taxAmount, that.taxAmount) &&
               Objects.equals(currency, that.currency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(originalAmount, finalAmount, deductionDetails, feeAmount, taxAmount, currency);
    }
    
    // 手动实现builder方法
    public static PaymentCalculationResultBuilder builder() {
        return new PaymentCalculationResultBuilder();
    }
    
    public static class PaymentCalculationResultBuilder {
        private BigDecimal originalAmount;
        private BigDecimal finalAmount;
        private Map<String, BigDecimal> deductionDetails;
        private BigDecimal feeAmount;
        private BigDecimal taxAmount;
        private String currency;
        
        public PaymentCalculationResultBuilder originalAmount(BigDecimal originalAmount) {
            this.originalAmount = originalAmount;
            return this;
        }
        
        public PaymentCalculationResultBuilder finalAmount(BigDecimal finalAmount) {
            this.finalAmount = finalAmount;
            return this;
        }
        
        public PaymentCalculationResultBuilder deductionDetails(Map<String, BigDecimal> deductionDetails) {
            this.deductionDetails = deductionDetails;
            return this;
        }
        
        public PaymentCalculationResultBuilder feeAmount(BigDecimal feeAmount) {
            this.feeAmount = feeAmount;
            return this;
        }
        
        public PaymentCalculationResultBuilder taxAmount(BigDecimal taxAmount) {
            this.taxAmount = taxAmount;
            return this;
        }
        
        public PaymentCalculationResultBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public PaymentCalculationResult build() {
            PaymentCalculationResult result = new PaymentCalculationResult();
            // 直接设置字段，不使用setter方法
            result.originalAmount = this.originalAmount;
            result.finalAmount = this.finalAmount;
            result.deductionDetails = this.deductionDetails;
            result.feeAmount = this.feeAmount;
            result.taxAmount = this.taxAmount;
            result.currency = this.currency;
            return result;
        }
    }
}