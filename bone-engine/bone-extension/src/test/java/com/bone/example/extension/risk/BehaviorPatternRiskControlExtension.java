package com.bone.example.extension.risk;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于用户行为模式的风险评估规则
 * 关注交易频率、时间模式、设备信息和交易类型序列
 */
@Extension(bizCode = "BEHAVIOR_PATTERN_RISK")
@Slf4j
public class BehaviorPatternRiskControlExtension implements RiskControlExtPoint {
    
    // 模拟行为分析服务
    private final UserBehaviorService behaviorService = new UserBehaviorService();
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context) {
        TransactionRequest request = context.getData();
        String userId = request.getUserId();
        String timestamp = request.getTimestamp();
        String deviceInfo = request.getDeviceInfo();
        String transactionType = request.getTransactionType();
        
        List<RiskAssessmentResult.RiskFactor> factors = new ArrayList<>();
        int totalScore = 0;
        
        // 1. 交易频率检查
        RiskAssessmentResult.RiskFactor frequencyFactor = checkTransactionFrequency(userId);
        if (frequencyFactor != null) {
            factors.add(frequencyFactor);
            totalScore += frequencyFactor.getRiskContribution();
        }
        
        // 2. 交易时间模式检查
        RiskAssessmentResult.RiskFactor timePatternFactor = checkTransactionTimePattern(userId, timestamp);
        if (timePatternFactor != null) {
            factors.add(timePatternFactor);
            totalScore += timePatternFactor.getRiskContribution();
        }
        
        // 3. 设备信息检查
        RiskAssessmentResult.RiskFactor deviceFactor = checkDeviceInfo(userId, deviceInfo);
        if (deviceFactor != null) {
            factors.add(deviceFactor);
            totalScore += deviceFactor.getRiskContribution();
        }
        
        // 4. 交易类型序列检查
        RiskAssessmentResult.RiskFactor sequenceFactor = checkTransactionTypeSequence(userId, transactionType);
        if (sequenceFactor != null) {
            factors.add(sequenceFactor);
            totalScore += sequenceFactor.getRiskContribution();
        }
        
        // 确定风险等级和决策
        RiskAssessmentResult.RiskLevel riskLevel = determineRiskLevel(totalScore);
        String decision = determineDecision(riskLevel);
        String rejectReason = riskLevel == RiskAssessmentResult.RiskLevel.HIGH ? 
                              "异常行为模式，触发风控规则" : null;
        
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
        return 30; // 较低优先级
    }
    
    /**
     * 检查交易频率
     */
    private RiskAssessmentResult.RiskFactor checkTransactionFrequency(String userId) {
        int transactionCount = behaviorService.getRecentTransactionCount(userId, 60 * 60 * 1000); // 1小时内
        
        if (transactionCount > 10) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("HIGH_TRANSACTION_FREQUENCY")
                .factorName("交易频率过高")
                .riskContribution(35)
                .description("短时间内交易次数异常增多")
                .build();
        } else if (transactionCount > 5) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("MEDIUM_TRANSACTION_FREQUENCY")
                .factorName("交易频率较高")
                .riskContribution(15)
                .description("交易频率高于正常水平")
                .build();
        }
        return null;
    }
    
    /**
     * 检查交易时间模式
     */
    private RiskAssessmentResult.RiskFactor checkTransactionTimePattern(String userId, String timestamp) {
        try {
            LocalDateTime transactionTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
            int hour = transactionTime.getHour();
            
            // 检查是否在用户不常用的时间进行交易
            List<Integer> activeHours = behaviorService.getUserActiveHours(userId);
            
            // 如果用户在深夜进行交易，且这不是他的活跃时段
            if ((hour < 6 || hour > 22) && !activeHours.contains(hour)) {
                return RiskAssessmentResult.RiskFactor.builder()
                    .factorId("UNUSUAL_TRANSACTION_TIME")
                    .factorName("异常交易时间")
                    .riskContribution(25)
                    .description("交易发生在用户非常规活跃时间")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error parsing timestamp: {}", timestamp, e);
        }
        return null;
    }
    
    /**
     * 检查设备信息
     */
    private RiskAssessmentResult.RiskFactor checkDeviceInfo(String userId, String deviceInfo) {
        if (deviceInfo == null) {
            return null;
        }
        
        // 检查是否使用新设备
        boolean isNewDevice = behaviorService.isNewDevice(userId, deviceInfo);
        if (isNewDevice) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("NEW_DEVICE")
                .factorName("新设备交易")
                .riskContribution(30)
                .description("用户使用新设备进行交易")
                .build();
        }
        return null;
    }
    
    /**
     * 检查交易类型序列
     */
    private RiskAssessmentResult.RiskFactor checkTransactionTypeSequence(String userId, String transactionType) {
        // 获取最近的交易类型序列
        List<String> recentTypes = behaviorService.getRecentTransactionTypes(userId, 5);
        
        // 检查是否有异常的交易类型组合
        if (recentTypes.contains("LOGIN") && !recentTypes.contains("VERIFY") && 
            (transactionType.equals("PAYMENT") || transactionType.equals("TRANSFER"))) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("SUSPICIOUS_TRANSACTION_SEQUENCE")
                .factorName("可疑交易序列")
                .riskContribution(40)
                .description("登录后未验证身份直接进行敏感操作")
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
     * 根据风险等级确定决策
     */
    private String determineDecision(RiskAssessmentResult.RiskLevel riskLevel) {
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
    static class UserBehaviorService {
        public int getRecentTransactionCount(String userId, long timeWindowMs) {
            // 模拟获取最近交易次数
            return 3; // 假设1小时内3次交易
        }
        
        public List<Integer> getUserActiveHours(String userId) {
            // 模拟获取用户活跃时段
            List<Integer> hours = new ArrayList<>();
            for (int i = 9; i < 22; i++) {
                hours.add(i);
            }
            return hours;
        }
        
        public boolean isNewDevice(String userId, String deviceInfo) {
            // 模拟检查是否为新设备
            return false;
        }
        
        public List<String> getRecentTransactionTypes(String userId, int count) {
            // 模拟获取最近交易类型
            List<String> types = new ArrayList<>();
            types.add("LOGIN");
            types.add("VERIFY");
            return types;
        }
    }
}