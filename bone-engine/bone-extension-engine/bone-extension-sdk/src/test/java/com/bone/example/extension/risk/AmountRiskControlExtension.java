package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;

/**
 * 基于交易金额的风险评估规则
 */
@Extension(bizCode = "AMOUNT_RISK")
public class AmountRiskControlExtension implements RiskControlExtPoint {
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context) {
        // 简化实现，直接返回null
        return null;
    }

    @Override
    public int getRulePriority() {
        return 0;
    }
}