package com.bone.example.extension.risk;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.BizContexts;
import com.bone.engine.extension.ExtensionExecutor;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 风控服务
 * 负责协调多个风控规则的执行，聚合风险评估结果，生成最终决策
 */
@Slf4j
public class RiskControlService {
    
    @Resource
    private ExtensionExecutor extensionExecutor;
    
    /**
     * 执行风险评估
     * @param request 交易请求
     * @param tenantCode 租户代码
     * @return 风险评估结果
     */
    public RiskAssessmentResult evaluateRisk(TransactionRequest request, String tenantCode) {
        try {
            // 创建并设置业务上下文
            try (BizContexts.ContextManager manager = BizContexts.use()) {
                manager.setTenantCode(tenantCode);
                manager.setBizDomain("RISK_CONTROL");
                
                // 执行所有风控规则
                List<RiskAssessmentResult> ruleResults = new ArrayList<>();
                
                extensionExecutor.executeMulti(RiskControlExtPoint.class, extPoint -> {
                    try {
                        RiskAssessmentResult result = extPoint.assessRisk(BizContexts.getContext(request));
                        if (result != null) {
                            ruleResults.add(result);
                            log.info("Risk rule {} executed with score: {}, level: {}",
                                    extPoint.getClass().getSimpleName(),
                                    result.getRiskScore(),
                                    result.getRiskLevel());
                        }
                    } catch (Exception e) {
                        log.error("Error executing risk rule: {}", extPoint.getClass().getSimpleName(), e);
                        // 单个规则执行失败不影响整体风控流程
                    }
                });
                
                // 聚合风控结果
                RiskAssessmentResult finalResult = aggregateRiskResults(ruleResults);
                
                // 记录风控决策日志
                logRiskDecision(request, finalResult);
                
                return finalResult;
            }
        } catch (Exception e) {
            log.error("Risk evaluation failed for tenant {}", tenantCode, e);
            
            // 风控服务异常时，默认返回高风险，确保安全
            return RiskAssessmentResult.builder()
                .riskLevel(RiskAssessmentResult.RiskLevel.HIGH)
                .riskScore(100)
                .decision("REJECT")
                .riskFactors(new ArrayList<>())
                .rejectReason("风控服务异常，为确保安全拒绝交易")
                .build();
        }
    }
    
    /**
     * 聚合多个风控规则的评估结果
     */
    private RiskAssessmentResult aggregateRiskResults(List<RiskAssessmentResult> results) {
        if (results.isEmpty()) {
            return RiskAssessmentResult.builder()
                .riskLevel(RiskAssessmentResult.RiskLevel.LOW)
                .riskScore(0)
                .decision("ACCEPT")
                .riskFactors(new ArrayList<>())
                .build();
        }
        
        // 1. 计算总分（各规则分数之和）
        int totalScore = results.stream()
            .mapToInt(RiskAssessmentResult::getRiskScore)
            .sum();
        
        // 2. 合并所有风险因子
        List<RiskAssessmentResult.RiskFactor> allFactors = results.stream()
            .flatMap(result -> result.getRiskFactors().stream())
            .collect(Collectors.toList());
        
        // 3. 确定最高风险等级
        RiskAssessmentResult.RiskLevel maxRiskLevel = results.stream()
            .map(RiskAssessmentResult::getRiskLevel)
            .max(Enum::compareTo)
            .orElse(RiskAssessmentResult.RiskLevel.LOW);
        
        // 4. 确定最终决策
        // 只要有一个规则判定为高风险，就拒绝
        boolean hasHighRisk = results.stream()
            .anyMatch(result -> result.getRiskLevel() == RiskAssessmentResult.RiskLevel.HIGH);
        
        String finalDecision;
        String rejectReason;
        
        if (hasHighRisk) {
            finalDecision = "REJECT";
            rejectReason = "多个风控规则综合评估为高风险";
        } else {
            // 根据总分和最高风险等级确定决策
            finalDecision = determineFinalDecision(totalScore, maxRiskLevel);
            rejectReason = finalDecision.equals("REJECT") ? "总分超过阈值" : null;
        }
        
        // 5. 构建最终结果
        return RiskAssessmentResult.builder()
            .riskLevel(maxRiskLevel)
            .riskScore(totalScore)
            .decision(finalDecision)
            .riskFactors(allFactors)
            .rejectReason(rejectReason)
            .build();
    }
    
    /**
     * 根据总分和最高风险等级确定最终决策
     */
    private String determineFinalDecision(int totalScore, RiskAssessmentResult.RiskLevel maxRiskLevel) {
        // 规则：
        // - 总分 >= 80：拒绝
        // - 总分 >= 50 或 最高等级为中风险：人工审核
        // - 其他：通过
        if (totalScore >= 80) {
            return "REJECT";
        } else if (totalScore >= 50 || maxRiskLevel == RiskAssessmentResult.RiskLevel.MEDIUM) {
            return "REVIEW";
        } else {
            return "ACCEPT";
        }
    }
    
    /**
     * 记录风控决策日志
     */
    private void logRiskDecision(TransactionRequest request, RiskAssessmentResult result) {
        // 构建日志内容
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("Risk control decision: ")
                .append("transactionId=").append(request.getTransactionId())
                .append(", userId=").append(request.getUserId())
                .append(", decision=").append(result.getDecision())
                .append(", riskLevel=").append(result.getRiskLevel())
                .append(", totalScore=").append(result.getRiskScore());
        
        // 添加风险因子信息
        if (!result.getRiskFactors().isEmpty()) {
            logBuilder.append(", riskFactors=[");
            String factorsStr = result.getRiskFactors().stream()
                .map(factor -> factor.getFactorName() + "(" + factor.getRiskContribution() + ")")
                .collect(Collectors.joining(", "));
            logBuilder.append(factorsStr).append("]");
        }
        
        // 根据决策类型输出不同级别的日志
        switch (result.getDecision()) {
            case "REJECT":
                log.warn(logBuilder.toString());
                break;
            case "REVIEW":
                log.info(logBuilder.toString());
                break;
            case "ACCEPT":
                log.debug(logBuilder.toString());
                break;
        }
        
        // 在实际项目中，这里还会将风控决策持久化到数据库或发送到消息队列
        saveRiskDecisionToDatabase(request, result);
    }
    
    /**
     * 保存风控决策到数据库（模拟）
     */
    private void saveRiskDecisionToDatabase(TransactionRequest request, RiskAssessmentResult result) {
        // 实际项目中会有数据库操作
        log.debug("Saving risk decision to database for transaction: {}", request.getTransactionId());
    }
}