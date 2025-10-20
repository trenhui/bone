package com.bone.engine.extension.metadata.example;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付结果
 */
@Data
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
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 交易时间
     */
    private LocalDateTime transactionTime;
    
    /**
     * 第三方支付平台返回的交易ID
     */
    private String thirdPartyTransactionId;
    
    /**
     * 附加信息
     */
    private Map<String, Object> extraInfo;
    
    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    
    // 手动添加 setter 方法以确保编译通过
    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public void setTransactionTime(LocalDateTime transactionTime) {
        this.transactionTime = transactionTime;
    }
    
    public void setThirdPartyTransactionId(String thirdPartyTransactionId) {
        this.thirdPartyTransactionId = thirdPartyTransactionId;
    }
    
    public void setExtraInfo(Map<String, Object> extraInfo) {
        this.extraInfo = extraInfo;
    }
}