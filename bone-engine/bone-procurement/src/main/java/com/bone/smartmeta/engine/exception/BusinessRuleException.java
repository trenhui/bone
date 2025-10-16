package com.bone.smartmeta.engine.exception;

import java.util.List;

/**
 * 业务规则异常
 */
public class BusinessRuleException extends RuntimeException {
    private List<String> violations;
    
    public BusinessRuleException(String message) {
        super(message);
    }
    
    public BusinessRuleException(String message, List<String> violations) {
        super(message);
        this.violations = violations;
    }
    
    public List<String> getViolations() {
        return violations;
    }
    
    public void setViolations(List<String> violations) {
        this.violations = violations;
    }
}