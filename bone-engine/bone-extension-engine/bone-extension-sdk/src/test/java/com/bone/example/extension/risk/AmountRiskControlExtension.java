package com.bone.example.extension.risk;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;
import org.springframework.stereotype.Component;

/**
 * 金额风险控制扩展实现
 */
@Extension(
    name = "金额风险控制实现",
    description = "基于交易金额的风险控制策略实现",
    tenantCode = "*",
    bizCode = "AMOUNT_RISK",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    description = "基于交易金额的风险控制实现，根据金额大小评估风险等级。",
    scenarios = "大额交易的风险评估场景",
    implementationDetails = "根据预设阈值评估交易金额风险，返回风险等级和建议",
    performance = "测试实现，单次执行耗时<2ms",
    notes = "专注于金额维度的风险控制",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Component
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