package com.bone.engine.extension.metadata.example;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付状态信息
 */
@Data
public class PaymentStatus {
    
    /**
     * 支付ID
     */
    private String paymentId;
    
    /**
     * 状态码
     * SUCCESS: 支付成功
     * FAILED: 支付失败
     * PENDING: 支付中
     * REFUNDED: 已退款
     * CLOSED: 已关闭
     */
    private String statusCode;
    
    /**
     * 状态描述
     */
    private String statusDescription;
    
    /**
     * 最近更新时间
     */
    private LocalDateTime lastUpdatedTime;
    
    /**
     * 交易完成时间
     */
    private LocalDateTime completedTime;
    
    /**
     * 是否已完成
     */
    public boolean isCompleted() {
        return "SUCCESS".equals(statusCode) || "FAILED".equals(statusCode) || "CLOSED".equals(statusCode) || "REFUNDED".equals(statusCode);
    }
    
    /**
     * 是否支付成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(statusCode);
    }
    
    // 手动添加 setter 方法以确保编译通过
    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }
    
    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }
    
    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }
    
    public void setLastUpdatedTime(LocalDateTime lastUpdatedTime) {
        this.lastUpdatedTime = lastUpdatedTime;
    }
    
    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }
}