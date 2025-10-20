package com.bone.example.extension.risk;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtensionContextManager;
import com.bone.engine.extension.ExtensionScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 风控服务
 * 负责协调多个风控规则的执行，聚合风险评估结果，生成最终决策
 */
@Slf4j
@Service
public class RiskControlService {
    
    // 常量定义
    private static final String RISK_CONTROL_LOG_PREFIX = "[RISK]";
    
    @Autowired
    private RiskControlExtPoint riskControlExtPoint;
    
    /**
     * 执行风险评估
     * @param request 交易请求
     * @param tenantCode 租户代码
     * @return 风险评估结果
     */
    public RiskAssessmentResult evaluateRisk(TransactionRequest request, String tenantCode) {
        String transactionId = request.getTransactionId();
        String userId = request.getUserId();
        
        log.info("{}: Starting risk evaluation for transaction: {}, user: {}", 
                 RISK_CONTROL_LOG_PREFIX, transactionId, userId);
        
        // 使用ExtensionContextManager创建上下文，支持try-with-resources模式
        try (ExtensionScope scope = ExtensionContextManager.withTenant(tenantCode)
                .withAttribute("transactionId", transactionId)
                .withAttribute("userId", userId)) {
            
            // 创建业务上下文
            BizContext<TransactionRequest> context = ExtensionContextManager.fromData(request);
            
            // 使用Stream API收集风控规则结果
            List<RiskAssessmentResult> ruleResults = List.of(
                riskControlExtPoint.assessRisk(context)
            );
            
            // 记录规则执行结果
            ruleResults.forEach(result -> 
                log.info("{} rule executed: {}, result: {}", 
                         RISK_CONTROL_LOG_PREFIX, 
                         riskControlExtPoint.getClass().getSimpleName(), 
                         result.getDecision())
            );
            
            // 聚合风控结果
            RiskAssessmentResult finalResult = aggregateRiskResults(ruleResults);
            
            // 记录风控决策日志
            logRiskDecision(request, finalResult);
            
            log.info("{} evaluation completed for transaction: {}, final decision: {}",
                     RISK_CONTROL_LOG_PREFIX, transactionId, finalResult.getDecision());
            
            return finalResult;
        } catch (Exception e) {
            log.error("{} Error during risk evaluation for transaction: {}, user: {}", 
                     RISK_CONTROL_LOG_PREFIX, transactionId, userId, e);
            
            // 异常情况下返回默认的高风险结果
            return RiskAssessmentResult.builder()
                    .riskLevel(RiskAssessmentResult.RiskLevel.HIGH)
                    .riskScore(100)
                    .decision("REJECT")
                    .riskFactors(new ArrayList<>() {{ add(new RiskAssessmentResult.RiskFactor("SYSTEM_ERROR", "Risk assessment failed", 100, "System error during risk assessment")); }} )
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
        
        // 1. 使用Stream API计算总分
        int totalScore = results.stream()
                .mapToInt(RiskAssessmentResult::getRiskScore)
                .sum();
        
        // 2. 使用Stream API合并所有风险因子
        List<RiskAssessmentResult.RiskFactor> allFactors = results.stream()
                .flatMap(result -> result.getRiskFactors().stream())
                .collect(Collectors.toList());
        
        // 3. 使用Stream API确定最高风险等级
        RiskAssessmentResult.RiskLevel maxRiskLevel = results.stream()
                .map(RiskAssessmentResult::getRiskLevel)
                .max(Comparator.naturalOrder())
                .orElse(RiskAssessmentResult.RiskLevel.LOW);
        
        // 4. 使用Stream API检查是否有高风险规则
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
        // 使用更简洁的条件语句
        if (totalScore >= 80) {
            return "REJECT";
        }
        return (totalScore >= 50 || maxRiskLevel == RiskAssessmentResult.RiskLevel.MEDIUM) ? "REVIEW" : "ACCEPT";
    }
    
    /**
     * 记录风控决策日志
     */
    private void logRiskDecision(TransactionRequest request, RiskAssessmentResult result) {
        // 使用结构化日志代替字符串拼接
        log.info("{0} Risk control decision: transactionId={1}, userId={2}, decision={3}, riskLevel={4}, totalScore={5}",
                RISK_CONTROL_LOG_PREFIX,
                request.getTransactionId(),
                request.getUserId(),
                result.getDecision(),
                result.getRiskLevel(),
                result.getRiskScore());
        
        // 添加风险因子信息
        if (!result.getRiskFactors().isEmpty()) {
            // 使用lambda表达式简化风险因子的处理
            String factors = result.getRiskFactors().stream()
                    .map(RiskAssessmentResult.RiskFactor::getFactorName)
                    .collect(Collectors.joining(", "));
            
            log.info("{0} Risk factors for transaction {1}: [{2}]", 
                    RISK_CONTROL_LOG_PREFIX, 
                    request.getTransactionId(), 
                    factors);
        }
        
        // 根据决策类型输出不同级别的日志
        switch (result.getDecision()) {
            case "REJECT":
                log.warn("{0} Transaction {1} rejected - User: {2}, Score: {3}", 
                        RISK_CONTROL_LOG_PREFIX, 
                        request.getTransactionId(), 
                        request.getUserId(), 
                        result.getRiskScore());
                break;
            case "REVIEW":
                log.info("{0} Transaction {1} requires review - User: {2}, Score: {3}", 
                        RISK_CONTROL_LOG_PREFIX, 
                        request.getTransactionId(), 
                        request.getUserId(), 
                        result.getRiskScore());
                break;
            case "ACCEPT":
                log.debug("{0} Transaction {1} accepted - User: {2}, Score: {3}", 
                        RISK_CONTROL_LOG_PREFIX, 
                        request.getTransactionId(), 
                        request.getUserId(), 
                        result.getRiskScore());
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