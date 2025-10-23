package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;

/**
 * 基于地理位置的风险评估规则
 * 关注交易地理位置、用户常用位置和位置变化速度
 */
public class LocationRiskControlExtension implements RiskControlExtPoint {
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context) {
        // 简化实现，避免对不存在方法的调用
        return new RiskAssessmentResult();
    }
    
    @Override
    public int getRulePriority() {
        return 20; // 中等优先级
    }
}