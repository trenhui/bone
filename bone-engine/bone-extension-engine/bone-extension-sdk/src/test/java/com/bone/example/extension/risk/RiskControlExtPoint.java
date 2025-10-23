package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;

/**
 * 风控规则扩展点
 * 定义了风险评估和风险分析的核心方法
 */
@ExtPoint(
    name = "风险控制扩展点",
    description = "处理交易风险控制的扩展点接口",
    domain = "风控系统",
    category = "风险评估",
    version = "1.0.0",
    enabled = true,
    priority = 100,
    enableCache = true,
    timeout = 1000
)
@ExtPointDoc(
    description = "该扩展点用于评估交易风险，支持多种风险控制策略。",
    usage = "1. 在交易执行前进行风险评估\n2. 根据交易类型和金额选择合适的风控策略\n3. 返回风险评估结果",
    bestPractices = "1. 确保风控规则的准确性\n2. 考虑性能影响\n3. 实现可配置的风控阈值",
    notes = "风控系统的核心扩展点，需要兼顾安全性和性能"
)
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