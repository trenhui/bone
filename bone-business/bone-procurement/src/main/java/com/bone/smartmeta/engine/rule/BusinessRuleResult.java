package com.bone.smartmeta.engine.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务规则执行结果
 */
public class BusinessRuleResult {
    private boolean passed;
    private List<String> violations;
    
    public BusinessRuleResult() {
        this.passed = true;
        this.violations = new ArrayList<>();
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    public List<String> getViolations() {
        return violations;
    }
    
    public void setViolations(List<String> violations) {
        this.violations = violations;
    }
    
    public void addViolation(String violation) {
        this.violations.add(violation);
        this.passed = false;
    }
}