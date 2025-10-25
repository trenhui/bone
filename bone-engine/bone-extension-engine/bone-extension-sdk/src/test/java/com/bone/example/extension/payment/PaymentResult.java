package com.bone.example.extension.payment;

import java.math.BigDecimal;

/**
 * 支付结果类
 * 封装支付操作的完整结果信息
 */
public class PaymentResult {
    /**
     * 支付ID
     */
    private String paymentId;
    
    /**
     * 支付状态
     */
    private String status;
    
    /**
     * 交易ID
     */
    private String transactionId;
    
    /**
     * 最终支付金额
     */
    private BigDecimal finalAmount;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 扣除的积分数量
     */
    private int pointsDeducted;
    
    /**
     * 错误码，支付失败时非空
     */
    private String errorCode;
    
    /**
     * 错误信息，支付失败时的详细说明
     */
    private String errorMessage;
    
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
    public int getPointsDeducted() { return pointsDeducted; }
    public void setPointsDeducted(int pointsDeducted) { this.pointsDeducted = pointsDeducted; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}