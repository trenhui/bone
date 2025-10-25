package com.bone.example.extension.risk;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险评估结果
 */
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
    public static class RiskFactor {
        private String factorId;
        private String factorName;
        private int riskContribution;
        private String description;
        
        // 手动实现RiskFactor的builder方法
        public static RiskFactorBuilder builder() {
            return new RiskFactorBuilder();
        }
        
        public static class RiskFactorBuilder {
            private String factorId;
            private String factorName;
            private int riskContribution;
            private String description;
            
            public RiskFactorBuilder factorId(String factorId) {
                this.factorId = factorId;
                return this;
            }
            
            public RiskFactorBuilder factorName(String factorName) {
                this.factorName = factorName;
                return this;
            }
            
            public RiskFactorBuilder riskContribution(int riskContribution) {
                this.riskContribution = riskContribution;
                return this;
            }
            
            public RiskFactorBuilder description(String description) {
                this.description = description;
                return this;
            }
            
            public RiskFactor build() {
                RiskFactor factor = new RiskFactor();
                factor.factorId = this.factorId;
                factor.factorName = this.factorName;
                factor.riskContribution = this.riskContribution;
                factor.description = this.description;
                return factor;
            }
        }
    }
    
    // 手动实现RiskAssessmentResult的builder方法
    public static RiskAssessmentResultBuilder builder() {
        return new RiskAssessmentResultBuilder();
    }
    
    public static class RiskAssessmentResultBuilder {
        private RiskLevel riskLevel;
        private int riskScore;
        private String decision;
        private List<RiskFactor> riskFactors = new ArrayList<>();
        private String rejectReason;
        
        public RiskAssessmentResultBuilder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }
        
        public RiskAssessmentResultBuilder riskScore(int riskScore) {
            this.riskScore = riskScore;
            return this;
        }
        
        public RiskAssessmentResultBuilder decision(String decision) {
            this.decision = decision;
            return this;
        }
        
        public RiskAssessmentResultBuilder riskFactors(List<RiskFactor> riskFactors) {
            this.riskFactors = riskFactors;
            return this;
        }
        
        public RiskAssessmentResultBuilder addRiskFactor(RiskFactor riskFactor) {
            this.riskFactors.add(riskFactor);
            return this;
        }
        
        public RiskAssessmentResultBuilder rejectReason(String rejectReason) {
            this.rejectReason = rejectReason;
            return this;
        }
        
        public RiskAssessmentResult build() {
            RiskAssessmentResult result = new RiskAssessmentResult();
            result.riskLevel = this.riskLevel;
            result.riskScore = this.riskScore;
            result.decision = this.decision;
            result.riskFactors = this.riskFactors;
            result.rejectReason = this.rejectReason;
            return result;
        }
    }
}
