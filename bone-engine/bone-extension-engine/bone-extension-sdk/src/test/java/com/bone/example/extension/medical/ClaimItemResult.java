package com.bone.example.extension.medical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.math.BigDecimal;

/**
 * 医疗理赔项目处理结果
 * 封装单个理赔项目的处理详情和赔付情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimItemResult {
    /**
     * 对应理赔请求中的项目ID
     */
    @NonNull
    private String itemId;
    
    /**
     * 项目处理状态
     */
    @NonNull
    private ItemStatus status;
    
    /**
     * 申请理赔金额
     */
    @NonNull
    private BigDecimal claimedAmount;
    
    /**
     * 批准赔付金额
     */
    private BigDecimal approvedAmount;
    
    /**
     * 拒绝赔付金额
     */
    private BigDecimal rejectedAmount;
    
    /**
     * 赔付比例
     */
    private BigDecimal reimbursementRate;
    
    /**
     * 拒绝原因，如适用
     */
    private String rejectionReason;
    
    /**
     * 项目状态枚举
     */
    public enum ItemStatus {
        /**
         * 完全批准
         */
        APPROVED,
        
        /**
         * 部分批准
         */
        PARTIALLY_APPROVED,
        
        /**
         * 完全拒绝
         */
        REJECTED,
        
        /**
         * 待审核
         */
        PENDING_REVIEW
    }
}