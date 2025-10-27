package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.example.extension.model.RiskAssessmentResult;
import com.bone.example.extension.model.TransactionRequest;
import com.bone.example.extension.point.RiskControlExtPoint;
import org.springframework.stereotype.Component;

/**
 * 行为模式风险控制扩展实现
 * <p>
 * 基于用户行为模式分析的风险控制策略，检测异常交易行为和模式。
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "行为模式风险控制扩展实现",
    description = "基于用户历史行为模式分析检测异常交易风险",
    tenantCode = "*",
    bizCode = "BEHAVIOR_RISK",
    condition = "#root.getBizContext().getData() != null && " +
               "(#root.getBizContext().getData().getDeviceInfo() != null || " +
               "#root.getBizContext().getData().getIpAddress() != null)",
    priority = 30,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "分析用户历史行为模式，识别异常交易行为和潜在风险。",
    scenarios = "适用于需要检测可疑交易行为的风控场景",
    implementationDetails = "基于用户历史交易行为、设备信息和操作模式进行风险评估",
    differences = "与金额风险控制不同，此实现关注用户行为维度而非交易金额",
    notes = "测试使用的简化实现，实际应用中需要整合用户画像和行为数据",
    author = "风险控制团队",
    createDate = "2024-01-01"
)
@Component
public class BehaviorPatternRiskControlExtension implements RiskControlExtPoint {

    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context) {
        // 简化实现
        RiskAssessmentResult result = new RiskAssessmentResult();
        result.setRiskLevel("LOW");
        result.setRiskScore(0);
        result.setDecision("ALLOW");
        return result;
    }
    
    @Override
    public int getRulePriority() {
        return 30; // 较低优先级
    }
}