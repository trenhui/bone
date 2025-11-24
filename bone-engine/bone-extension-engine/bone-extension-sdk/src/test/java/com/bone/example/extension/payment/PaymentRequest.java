package com.bone.example.extension.payment;

import java.math.BigDecimal;

/**
 * 支付请求基础类
 * <p>
 * 封装支付请求的通用属性和方法，供不同模块的支付相关请求类继承使用
 */
public class PaymentRequest {
    protected String orderId;
    protected String userId;
    protected BigDecimal amount;
    protected String paymentMethod;
    
    /**
     * 获取订单ID
     * 
     * @return 订单ID
     */
    public String getOrderId() {
        return orderId;
    }
    
    /**
     * 设置订单ID
     * 
     * @param orderId 订单ID
     */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    /**
     * 获取用户ID
     * 
     * @return 用户ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * 设置用户ID
     * 
     * @param userId 用户ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    /**
     * 获取支付金额
     * 
     * @return 支付金额
     */
    public BigDecimal getAmount() {
        return amount;
    }
    
    /**
     * 设置支付金额
     * 
     * @param amount 支付金额
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    /**
     * 获取支付方式
     * 
     * @return 支付方式
     */
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    /**
     * 设置支付方式
     * 
     * @param paymentMethod 支付方式
     */
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}