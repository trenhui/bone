package com.bone.example.extension.risk;

import com.bone.engine.extension.BizContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;

/**
 * 风控服务
 * 负责协调多个风控规则的执行，聚合风险评估结果，生成最终决策
 */
@Slf4j
public class RiskControlService {
    
    // 常量定义
    private static final String RISK_CONTROL_LOG_PREFIX = "Risk control";
    
    @Autowired
    private RiskControlExtPoint riskControlExtPoint;
    
    /**
     * 执行风险评估
     * @param request 交易请求
     * @param tenantCode 租户代码
     * @return 风险评估结果
     */
    public RiskAssessmentResult evaluateRisk(TransactionRequest request, String tenantCode) {
        log.info("{}: Starting risk evaluation for transaction: {}, user: {}", 
                 RISK_CONTROL_LOG_PREFIX, request.getTransactionId(), request.getUserId());
        
        // 创建业务上下文
        BizContext<TransactionRequest> context = BizContext.create();
        context.setData(request);
        context.setBizCode(tenantCode);
        
        // 执行风控规则
        List<RiskAssessmentResult> ruleResults = new ArrayList<>();
        
        // 调用风控扩展点执行风控评估
        RiskAssessmentResult result = riskControlExtPoint.assessRisk(context);
        ruleResults.add(result);
        log.info("{} rule executed: {}, result: {}", 
                 RISK_CONTROL_LOG_PREFIX, 
                 riskControlExtPoint.getClass().getSimpleName(), 
                 result.getDecision());
            
        // 聚合风控结果
        RiskAssessmentResult finalResult = aggregateRiskResults(ruleResults);
        
        // 记录风控决策日志
        logRiskDecision(request, finalResult);
        
        log.info("{} evaluation completed for transaction: {}, final decision: {}",
                 RISK_CONTROL_LOG_PREFIX, request.getTransactionId(), finalResult.getDecision());
        
        return finalResult;
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
        int totalScore = 0;
        for (RiskAssessmentResult result : results) {
            totalScore += result.getRiskScore();
        }
        
        // 2. 合并所有风险因子
        List<RiskAssessmentResult.RiskFactor> allFactors = new ArrayList<>();
        for (RiskAssessmentResult result : results) {
            allFactors.addAll(result.getRiskFactors());
        }
        
        // 3. 确定最高风险等级
        RiskAssessmentResult.RiskLevel maxRiskLevel = RiskAssessmentResult.RiskLevel.LOW;
        for (RiskAssessmentResult result : results) {
            if (result.getRiskLevel().compareTo(maxRiskLevel) > 0) {
                maxRiskLevel = result.getRiskLevel();
            }
        }
        
        // 4. 确定最终决策
        // 只要有一个规则判定为高风险，就拒绝
        boolean hasHighRisk = false;
        for (RiskAssessmentResult result : results) {
            if (result.getRiskLevel() == RiskAssessmentResult.RiskLevel.HIGH) {
                hasHighRisk = true;
                break;
            }
        }
        
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
            StringBuilder factorsBuilder = new StringBuilder();
            for (int i = 0; i < result.getRiskFactors().size(); i++) {
                RiskAssessmentResult.RiskFactor factor = result.getRiskFactors().get(i);
                factorsBuilder.append(factor.getFactorName()).append("(").append(factor.getRiskContribution()).append(")");
                if (i < result.getRiskFactors().size() - 1) {
                    factorsBuilder.append(", ");
                }
            }
            logBuilder.append(factorsBuilder).append("]");
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