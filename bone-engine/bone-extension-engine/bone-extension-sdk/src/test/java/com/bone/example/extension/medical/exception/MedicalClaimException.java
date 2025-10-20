package com.bone.example.extension.medical.exception;

/**
 * 医疗保险理赔模块的自定义异常类
 * 用于理赔处理过程中的业务异常
 */
public class MedicalClaimException extends RuntimeException {
    
    private String errorCode;
    private String claimId;
    
    public MedicalClaimException(String message) {
        super(message);
    }
    
    public MedicalClaimException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public MedicalClaimException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public MedicalClaimException(String message, String errorCode, String claimId) {
        super(message);
        this.errorCode = errorCode;
        this.claimId = claimId;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getClaimId() {
        return claimId;
    }
}