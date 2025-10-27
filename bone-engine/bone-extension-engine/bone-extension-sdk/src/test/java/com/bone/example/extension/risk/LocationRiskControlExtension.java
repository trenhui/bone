package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.example.extension.model.RiskAssessmentResult;
import com.bone.example.extension.model.TransactionRequest;
import com.bone.example.extension.point.RiskControlExtPoint;
import org.springframework.stereotype.Component;

/**
 * 基于地理位置的风险评估规则
 * <p>
 * 关注交易地理位置、用户常用位置和位置变化速度，检测异常位置交易。
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "地理位置风险控制扩展实现",
    description = "基于用户交易地理位置和历史位置模式检测异常风险",
    tenantCode = "*",
    bizCode = "LOCATION_RISK",
    condition = "#root.getBizContext().getData() != null && " +
               "#root.getBizContext().getData().getLocation() != null",
    priority = 20,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "分析用户交易地理位置，检测异常位置交易和可疑位置变化。",
    scenarios = "适用于需要位置风险评估的场景，如异地登录、异常位置交易",
    implementationDetails = "基于用户历史位置数据、当前交易位置和位置变化速度进行风险评估",
    differences = "专注于地理位置维度的风险评估，与金额和行为模式风险形成互补",
    notes = "测试使用的简化实现，实际应用中需要整合地理位置服务和历史位置数据库",
    author = "风险控制团队",
    createDate = "2024-01-01"
)
@Component
public class LocationRiskControlExtension implements RiskControlExtPoint {
    
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
        return 20; // 中等优先级
    }
}