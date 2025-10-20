package com.bone.example.extension.risk.exception;

/**
 * 风控模块的自定义异常类
 * 用于风控评估过程中的业务异常
 */
public class RiskControlException extends RuntimeException {
    
    private String errorCode;
    private String riskFactor;
    
    public RiskControlException(String message) {
        super(message);
    }
    
    public RiskControlException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public RiskControlException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public RiskControlException(String message, String errorCode, String riskFactor) {
        super(message);
        this.errorCode = errorCode;
        this.riskFactor = riskFactor;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getRiskFactor() {
        return riskFactor;
    }
}