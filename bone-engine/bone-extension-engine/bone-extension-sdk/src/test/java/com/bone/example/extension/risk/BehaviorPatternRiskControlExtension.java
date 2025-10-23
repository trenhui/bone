package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;

/**
 * 行为模式风险控制扩展实现
 */
public class BehaviorPatternRiskControlExtension implements RiskControlExtPoint {

    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context) {
        // 简化实现，直接返回空结果
        return new RiskAssessmentResult();
    }

    @Override
    public int getRulePriority() {
        return 30; // 保持原有优先级
    }
}