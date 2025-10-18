package com.bone.example.extension.medical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 医疗保险理赔结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalClaimResult {
    private String claimId;
    private ClaimStatus status;
    private BigDecimal totalClaimAmount;
    private BigDecimal approvedAmount;
    private BigDecimal rejectedAmount;
    private List<ClaimItemResult> itemResults;
    private String rejectionReason;
    private Date processingDate;
    private String processorId;
    private String paymentStatus;
    private Date paymentDate;
    private String transactionId;
    private String remarks;
    
    /**
     * 理赔状态枚举
     */
    public enum ClaimStatus {
        SUBMITTED, PROCESSING, APPROVED, PARTIALLY_APPROVED, REJECTED, PAID, CANCELLED
    }
    
    /**
     * 理赔项目结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimItemResult {
        private String itemName;
        private String itemCode;
        private BigDecimal claimedAmount;
        private BigDecimal approvedAmount;
        private BigDecimal rejectionAmount;
        private boolean approved;
        private String rejectionReason;
        private BigDecimal reimbursementRate;
    }
}