package com.bone.example.extension.payment;

import java.math.BigDecimal;

/**
 * 支付请求测试类
 */
public class PaymentTestRequest {
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String paymentMethod;
    private String couponId;
    private int pointsToDeduct;
    
    // Getter和Setter方法
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCouponId() {
        return couponId;
    }
    
    public void setCouponId(String couponId) {
        this.couponId = couponId;
    }
    
    public int getPointsToDeduct() {
        return pointsToDeduct;
    }
    
    public void setPointsToDeduct(int pointsToDeduct) {
        this.pointsToDeduct = pointsToDeduct;
    }
}