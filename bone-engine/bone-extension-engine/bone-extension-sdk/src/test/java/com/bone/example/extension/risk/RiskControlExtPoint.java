package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;

/**
 * 风控规则扩展点
 * 定义了风险评估和风险分析的核心方法
 */
@ExtPoint
public interface RiskControlExtPoint {
    
    /**
     * 评估交易风险
     * @param context 业务上下文，包含交易请求信息
     * @return 风险评估结果
     */
    RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context);
    
    /**
     * 获取规则的优先级
     * @return 优先级，数值越小优先级越高
     */
    int getRulePriority();
}

/**
 * 交易请求类
 */
interface TransactionRequest {
    String getTransactionId();
    String getUserId();
    double getAmount();
    String getTransactionType();
    String getTimestamp();
    String getLocation();
    String getDeviceInfo();
    String getIpAddress();
}