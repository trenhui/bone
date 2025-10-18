package com.bone.example.extension.medical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 医疗保险理赔请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalClaimRequest {
    private String claimId;
    private String userId;
    private String policyNo;
    private ClaimType claimType;
    private Date medicalDate;
    private String hospitalName;
    private String hospitalLevel;
    private BigDecimal totalAmount;
    private List<ClaimItem> items;
    private String diagnosis;
    private String admissionType;
    private Date admissionDate;
    private Date dischargeDate;
    private String bankAccountInfo;
    private String remarks;
    
    /**
     * 理赔类型枚举
     */
    public enum ClaimType {
        OUTPATIENT, INPATIENT, SPECIAL_TREATMENT
    }
    
    /**
     * 理赔项目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimItem {
        private String itemName;
        private String itemCode;
        private String category;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal totalAmount;
        private boolean covered;
        private String prescriptionNo;
    }
}