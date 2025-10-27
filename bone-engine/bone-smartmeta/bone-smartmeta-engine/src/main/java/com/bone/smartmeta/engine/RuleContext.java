package com.bone.smartmeta.engine;

/**
 * 规则执行上下文
 */
import java.util.Map;

public class RuleContext {
    private String requestId;
    private String userId;
    private Map<String, Object> parameters;
    private boolean failFast;
    
    public RuleContext() {
        this.parameters = new java.util.HashMap<>();
        this.failFast = true;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    public boolean isFailFast() {
        return failFast;
    }
    
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }
    
    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        return (T) this.parameters.get(key);
    }
    
    public boolean containsParameter(String key) {
        return this.parameters.containsKey(key);
    }
}