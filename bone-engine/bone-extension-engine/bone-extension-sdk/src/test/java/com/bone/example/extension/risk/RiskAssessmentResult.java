package com.bone.example.extension.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentResult {
    private RiskLevel riskLevel;
    private int riskScore;
    private String decision;
    private List<RiskFactor> riskFactors;
    private String rejectReason;
    
    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }
    
    /**
     * 风险因子
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorId;
        private String factorName;
        private int riskContribution;
        private String description;
    }
}