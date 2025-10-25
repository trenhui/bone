package com.bone.engine.extension.metadata.example;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;

/**
 * 支付扩展点示例接口
 * <p>
 * 演示如何使用新的元数据属性来描述扩展点
 * </p>
 * 
 * @since 1.0.0
 */
@ExtPoint(
    name = "支付扩展点",
    description = "支付处理扩展点，支持多种支付方式的实现",
    version = "1.0.0",
    enabled = true
)
@ExtPointDoc(
    title = "支付服务扩展点",
    domain = "支付",
    description = "提供多种支付方式的统一接入接口",
    usage = "用于处理订单支付、会员支付等场景",
    bestPractices = "建议实现时注意事务一致性和错误处理"
)
public interface PaymentExtPoint {
    
    /**
     * 处理支付请求
     * 执行具体的支付逻辑，返回支付结果
     * 
     * @param request 支付请求参数，包含订单号、金额、支付方式等信息
     * @return 支付结果，包含支付ID、状态、消息等信息
     * @throws IllegalArgumentException 当请求参数无效时抛出
     * @throws RuntimeException 当支付处理过程中发生异常时抛出
     */
    PaymentResult processPayment(PaymentRequest request);
    
    /**
     * 验证支付参数
     * 在支付处理前验证请求参数的有效性
     * 
     * @param request 支付请求参数
     * @return 验证结果，包含是否有效和错误信息
     */
    ValidationResult validatePayment(PaymentRequest request);
    
    /**
     * 获取支付状态
     * 根据支付ID查询支付的当前状态
     * 
     * @param paymentId 支付ID
     * @return 支付状态信息，包含状态码、状态描述、更新时间等
     * @throws IllegalArgumentException 当支付ID无效时抛出
     */
    PaymentStatus getPaymentStatus(String paymentId);
    
    // 内部类定义
    class PaymentRequest {
        private String orderId;
        private java.math.BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String userId;
        private String clientIp;
        
        // Getters and Setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    }
    
    class PaymentResult {
        private String paymentId;
        private String status;
        private String message;
        private java.math.BigDecimal paidAmount;
        private long paidTime;
        private String transactionId;
        
        // Getters and Setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public java.math.BigDecimal getPaidAmount() { return paidAmount; }
        public void setPaidAmount(java.math.BigDecimal paidAmount) { this.paidAmount = paidAmount; }
        
        public long getPaidTime() { return paidTime; }
        public void setPaidTime(long paidTime) { this.paidTime = paidTime; }
        
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    }
    
    class ValidationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;
        private java.util.Map<String, String> details;
        
        // Getters and Setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public java.util.Map<String, String> getDetails() {
            if (details == null) {
                details = new java.util.HashMap<>();
            }
            return details;
        }
        
        public void setDetails(java.util.Map<String, String> details) { this.details = details; }
    }
    
    class PaymentStatus {
        private String paymentId;
        private String statusCode;
        private String statusDesc;
        private long updateTime;
        private String channel;
        private java.math.BigDecimal amount;
        
        // Getters and Setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getStatusCode() { return statusCode; }
        public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
        
        public String getStatusDesc() { return statusDesc; }
        public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }
        
        public long getUpdateTime() { return updateTime; }
        public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }
        
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
    }
}