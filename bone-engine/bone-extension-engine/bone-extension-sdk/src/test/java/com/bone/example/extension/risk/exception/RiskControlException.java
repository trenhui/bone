package com.bone.example.extension.risk.exception;

import com.bone.engine.extension.exception.BusinessException;

/**
 * 风控异常类
 * <p>
 * 提供风控领域特有的异常功能和业务方法
 */
public class RiskControlException extends BusinessException {
    private static final long serialVersionUID = 1L;
    
    /**
     * 构建风控异常
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public RiskControlException(String errorCode, String message) {
        super("RISK_CONTROL", errorCode, message);
    }
    
    /**
     * 构建风控异常
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常原因
     */
    public RiskControlException(String errorCode, String message, Throwable cause) {
        super("RISK_CONTROL", errorCode, message, cause);
    }
    
    /**
     * 创建风控规则拦截异常
     * @param ruleId 规则ID
     * @param message 拦截原因
     * @return 风控异常实例
     */
    public static RiskControlException ruleRejected(String ruleId, String message) {
        return new RiskControlException("RULE_REJECTED", "规则拦截[" + ruleId + "]: " + message);
    }
    
    /**
     * 创建风控评分异常
     * @param scoreThreshold 评分阈值
     * @param actualScore 实际评分
     * @return 风控异常实例
     */
    public static RiskControlException scoreBelowThreshold(int scoreThreshold, int actualScore) {
        return new RiskControlException("SCORE_BELOW_THRESHOLD", 
                "评分未达标: 阈值=" + scoreThreshold + ", 实际=" + actualScore);
    }
}