package com.bone.procurement.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务规则执行结果
 * 用于封装业务规则验证的结果信息
 */
public class RuleResult {
    private boolean success;
    private List<String> messages = new ArrayList<>();
    private Object data;
    
    public RuleResult(boolean success) {
        this.success = success;
    }
    
    public RuleResult(boolean success, String message) {
        this.success = success;
        if (message != null) {
            this.messages.add(message);
        }
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public List<String> getMessages() {
        return messages;
    }
    
    public void setMessages(List<String> messages) {
        this.messages = messages;
    }
    
    public void addMessage(String message) {
        this.messages.add(message);
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    public static RuleResult success() {
        return new RuleResult(true);
    }
    
    public static RuleResult failure(String message) {
        return new RuleResult(false, message);
    }
}
