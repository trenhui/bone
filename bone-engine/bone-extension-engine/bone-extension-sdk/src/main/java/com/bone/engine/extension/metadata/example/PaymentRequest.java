package com.bone.engine.extension.metadata.example;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付请求参数
 */
@Data
public class PaymentRequest {
    
    /**
     * 支付金额
     */
    private BigDecimal amount;
    
    /**
     * 支付方式
     */
    private String paymentMethod;
    
    /**
     * 订单号
     */
    private String orderId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 货币类型
     */
    private String currency;
    
    /**
     * 附加参数
     */
    private Map<String, Object> extraParams;
    
    // 手动添加 setter 方法以确保编译通过
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public Map<String, Object> getExtraParams() {
        return extraParams;
    }
}