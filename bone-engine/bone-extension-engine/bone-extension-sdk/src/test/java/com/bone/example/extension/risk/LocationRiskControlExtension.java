package com.bone.example.extension.risk;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基于地理位置的风险评估规则
 * 关注交易地理位置、用户常用位置和位置变化速度
 */
@Extension(bizCode = "LOCATION_RISK")
@Slf4j
public class LocationRiskControlExtension implements RiskControlExtPoint {
    
    // 模拟位置服务
    private final LocationService locationService = new LocationService();
    private final UserLocationHistoryService historyService = new UserLocationHistoryService();
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context) {
        TransactionRequest request = context.getData();
        String userId = request.getUserId();
        String transactionLocation = request.getLocation();
        String ipAddress = request.getIpAddress();
        
        List<RiskAssessmentResult.RiskFactor> factors = new ArrayList<>();
        int totalScore = 0;
        
        // 1. IP地址与地理位置一致性检查
        RiskAssessmentResult.RiskFactor ipLocationFactor = checkIpLocationConsistency(ipAddress, transactionLocation);
        if (ipLocationFactor != null) {
            factors.add(ipLocationFactor);
            totalScore += ipLocationFactor.getRiskContribution();
        }
        
        // 2. 用户常用位置检查
        RiskAssessmentResult.RiskFactor commonLocationFactor = checkCommonUserLocation(userId, transactionLocation);
        if (commonLocationFactor != null) {
            factors.add(commonLocationFactor);
            totalScore += commonLocationFactor.getRiskContribution();
        }
        
        // 3. 位置变化速度检查
        RiskAssessmentResult.RiskFactor locationSpeedFactor = checkLocationChangeSpeed(userId, transactionLocation);
        if (locationSpeedFactor != null) {
            factors.add(locationSpeedFactor);
            totalScore += locationSpeedFactor.getRiskContribution();
        }
        
        // 4. 高风险地区检查
        RiskAssessmentResult.RiskFactor highRiskRegionFactor = checkHighRiskRegion(transactionLocation);
        if (highRiskRegionFactor != null) {
            factors.add(highRiskRegionFactor);
            totalScore += highRiskRegionFactor.getRiskContribution();
        }
        
        // 确定风险等级和决策
        RiskAssessmentResult.RiskLevel riskLevel = determineRiskLevel(totalScore);
        String decision = determineDecision(riskLevel);
        String rejectReason = riskLevel == RiskAssessmentResult.RiskLevel.HIGH ? 
                              "异常地理位置，触发风控规则" : null;
        
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
        return 20; // 中等优先级
    }
    
    /**
     * 检查IP地址与地理位置的一致性
     */
    private RiskAssessmentResult.RiskFactor checkIpLocationConsistency(String ipAddress, String transactionLocation) {
        if (ipAddress == null || transactionLocation == null) {
            return null;
        }
        
        String ipBasedLocation = locationService.getLocationByIp(ipAddress);
        if (ipBasedLocation != null && !transactionLocation.contains(ipBasedLocation)) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("IP_LOCATION_MISMATCH")
                .factorName("IP地址与地理位置不匹配")
                .riskContribution(35)
                .description("IP地址显示的位置与交易位置不一致")
                .build();
        }
        return null;
    }
    
    /**
     * 检查用户常用位置
     */
    private RiskAssessmentResult.RiskFactor checkCommonUserLocation(String userId, String transactionLocation) {
        List<String> commonLocations = historyService.getCommonUserLocations(userId);
        boolean isCommonLocation = commonLocations.stream()
            .anyMatch(location -> transactionLocation.contains(location));
        
        if (!isCommonLocation && !commonLocations.isEmpty()) {
            return RiskAssessmentResult.RiskFactor.builder()
                .factorId("UNCOMMON_LOCATION")
                .factorName("非常规交易位置")
                .riskContribution(25)
                .description("交易发生在用户不常用的位置")
                .build();
        }
        return null;
    }
    
    /**
     * 检查位置变化速度
     */
    private RiskAssessmentResult.RiskFactor checkLocationChangeSpeed(String userId, String transactionLocation) {
        String lastLocation = historyService.getLastTransactionLocation(userId);
        long lastTransactionTime = historyService.getLastTransactionTime(userId);
        
        if (lastLocation != null && lastTransactionTime > 0) {
            // 计算两次交易的时间间隔（毫秒）
            long currentTime = System.currentTimeMillis();
            long timeInterval = currentTime - lastTransactionTime;
            
            // 计算理论上需要的最小时间（假设飞机速度为1000km/h）
            double distance = locationService.calculateDistance(lastLocation, transactionLocation);
            long minRequiredTime = (long) (distance / 1000 * 3600 * 1000); // 转换为毫秒
            
            // 如果实际时间远小于理论所需时间，认为存在风险
            if (timeInterval < minRequiredTime / 2) {
                return RiskAssessmentResult.RiskFactor.builder()
                    .factorId("IMPOSSIBLE_TRAVEL_SPEED")
                    .factorName("不可能的移动速度")
                    .riskContribution(40)
                    .description("用户移动速度超过合理范围")
                    .build();
            }
        }
        return null;
    }
    
    /**
     * 检查高风险地区
     */
    private RiskAssessmentResult.RiskFactor checkHighRiskRegion(String location) {
        List<String> highRiskRegions = Arrays.asList("高风险地区1", "高风险地区2", "国外地区A");
        
        for (String region : highRiskRegions) {
            if (location.contains(region)) {
                return RiskAssessmentResult.RiskFactor.builder()
                    .factorId("HIGH_RISK_REGION")
                    .factorName("高风险地区交易")
                    .riskContribution(30)
                    .description("交易发生在高风险地区")
                    .build();
            }
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
    static class LocationService {
        public String getLocationByIp(String ipAddress) {
            // 模拟根据IP获取位置
            return "北京";
        }
        
        public double calculateDistance(String location1, String location2) {
            // 模拟计算两地距离
            return 200.0; // 公里
        }
    }
    
    static class UserLocationHistoryService {
        public List<String> getCommonUserLocations(String userId) {
            // 模拟获取用户常用位置
            return Arrays.asList("北京", "上海");
        }
        
        public String getLastTransactionLocation(String userId) {
            // 模拟获取上次交易位置
            return "北京";
        }
        
        public long getLastTransactionTime(String userId) {
            // 模拟获取上次交易时间
            return System.currentTimeMillis() - 3600000; // 1小时前
        }
    }
}