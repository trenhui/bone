package com.bone.example.extension.risk;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于交易金额的风险评估规则
 * 关注交易金额大小、用户历史交易模式和金额变化趋势
 */
@Extension(bizCode = "AMOUNT_RISK")
@Slf4j
public class AmountRiskControlExtension implements RiskControlExtPoint {
    
    // 模拟用户历史交易服务
    private final UserTransactionHistoryService historyService = new UserTransactionHistoryService();
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context) {
        TransactionRequest request = context.getData();
        String userId = request.getUserId();
        double amount = request.getAmount();
        
        List<RiskAssessmentResult.RiskFactor> factors = new ArrayList<>();
        int totalScore = 0;
        
        // 1. 基础金额风险检查
        RiskAssessmentResult.RiskFactor baseAmountFactor = checkBaseAmountRisk(amount);
        if (baseAmountFactor != null) {
            factors.add(baseAmountFactor);
            totalScore += baseAmountFactor.getRiskContribution();
        }
        
        // 2. 用户历史交易模式检查
        RiskAssessmentResult.RiskFactor userPatternFactor = checkUserTransactionPattern(userId, amount);
        if (userPatternFactor != null) {
            factors.add(userPatternFactor);
            totalScore += userPatternFactor.getRiskContribution();
        }
        
        // 3. 交易金额变化趋势检查
        RiskAssessmentResult.RiskFactor trendFactor = checkAmountTrend(userId, amount);
        if (trendFactor != null) {
            factors.add(trendFactor);
            totalScore += trendFactor.getRiskContribution();
        }
        
        // 确定风险等级和决策
        RiskAssessmentResult.RiskLevel riskLevel = determineRiskLevel(totalScore);
        String decision = determineDecision(riskLevel, totalScore);
        String rejectReason = riskLevel == RiskAssessmentResult.RiskLevel.HIGH ? 
                              "交易金额异常，触发风控规则" : null;
        
        return RiskAssessmentResult.builder()
            .riskLevel(riskLevel)
            .riskScore(totalScore)
            .decision(decision)
            .riskFactors(factors)
            .rejectReason(rejectReason)
            .build();
    }
    
    @Override
    public int getRulePriority() {
        return 10; // 优先级较高，金额风险通常是重要指标
    }
    
    /**
     * 基础金额风险检查
     */
    private RiskAssessmentResult.RiskFactor checkBaseAmountRisk(double amount) {
        // 根据金额大小设置风险分值
        if (amount > 50000) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("HIGH_AMOUNT")
                .factorName("大额交易")
                .riskContribution(40)
                .description("交易金额超过50000元，属于大额交易")
                .build();
        } else if (amount > 10000) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("MEDIUM_HIGH_AMOUNT")
                .factorName("中高金额交易")
                .riskContribution(20)
                .description("交易金额超过10000元，需要关注")
                .build();
        }
        return null;
    }
    
    /**
     * 用户历史交易模式检查
     */
    private RiskAssessmentResult.RiskFactor checkUserTransactionPattern(String userId, double amount) {
        // 获取用户历史平均交易金额
        double avgTransactionAmount = historyService.getAverageTransactionAmount(userId);
        
        // 如果当前交易金额是历史平均值的5倍以上，认为有风险
        if (avgTransactionAmount > 0 && amount > avgTransactionAmount * 5) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("ABNORMAL_AMOUNT_PATTERN")
                .factorName("异常交易金额模式")
                .riskContribution(30)
                .description("当前交易金额显著高于用户历史平均水平")
                .build();
        }
        return null;
    }
    
    /**
     * 交易金额变化趋势检查
     */
    private RiskAssessmentResult.RiskFactor checkAmountTrend(String userId, double amount) {
        // 获取最近几笔交易的金额趋势
        double recentAvgAmount = historyService.getRecentAverageAmount(userId, 3);
        
        // 如果近期交易金额呈现快速上升趋势，认为有风险
        if (recentAvgAmount > 0 && amount > recentAvgAmount * 3) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("RAPID_AMOUNT_INCREASE")
                .factorName("交易金额快速上升")
                .riskContribution(25)
                .description("交易金额相比近期有明显上升趋势")
                .build();
        }
        return null;
    }
    
    /**
     * 根据分数确定风险等级
     */
    private RiskAssessmentResult.RiskLevel determineRiskLevel(int score) {
        if (score >= 60) {
            return RiskAssessmentResult.RiskLevel.HIGH;
        } else if (score >= 30) {
            return RiskAssessmentResult.RiskLevel.MEDIUM;
        } else {
            return RiskAssessmentResult.RiskLevel.LOW;
        }
    }
    
    /**
     * 根据风险等级和分数确定决策
     */
    private String determineDecision(RiskAssessmentResult.RiskLevel riskLevel, int score) {
        switch (riskLevel) {
            case HIGH:
                return "REJECT";
            case MEDIUM:
                return "REVIEW";
            case LOW:
                return "ACCEPT";
            default:
                return "REVIEW";
        }
    }
    
    // 模拟服务类
    static class UserTransactionHistoryService {
        public double getAverageTransactionAmount(String userId) {
            // 模拟获取用户历史平均交易金额
            return 5000.0; // 假设平均5000元
        }
        
        public double getRecentAverageAmount(String userId, int recentCount) {
            // 模拟获取最近几笔交易的平均金额
            return 6000.0; // 假设最近平均6000元
        }
    }
}