package com.bone.procurement.engine.model;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 审批结果类
 * 存储采购订单审批流程的执行结果信息
 */
@Data
@Builder
public class ApprovalResult {
    
    /**
     * 审批结果状态：APPROVED(已通过)、REJECTED(已拒绝)、PENDING(待审批)、ERROR(错误)
     */
    private String status;
    
    /**
     * 审批通过标志
     */
    private boolean approved;
    
    /**
     * 审批意见
     */
    private String comment;
    
    /**
     * 审批人ID
     */
    private String approverId;
    
    /**
     * 审批人名称
     */
    private String approverName;
    
    /**
     * 审批时间
     */
    private LocalDateTime approvalTime;
    
    /**
     * 审批级别
     */
    private int approvalLevel;
    
    /**
     * 下一审批级别
     */
    private Integer nextApprovalLevel;
    
    /**
     * 下一审批人ID
     */
    private String nextApproverId;
    
    /**
     * 审批流程定义ID
     */
    private String flowDefinitionId;
    
    /**
     * 审批流程实例ID
     */
    private String flowInstanceId;
    
    /**
     * 验证错误列表（如果有）
     */
    private List<RuleValidationResult> validationErrors;
    
    /**
     * 创建审批通过的结果
     */
    public static ApprovalResult approved(String approverId, String approverName) {
        return ApprovalResult.builder()
                .status("APPROVED")
                .approved(true)
                .approverId(approverId)
                .approverName(approverName)
                .approvalTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建审批拒绝的结果
     */
    public static ApprovalResult rejected(String approverId, String approverName, String comment) {
        return ApprovalResult.builder()
                .status("REJECTED")
                .approved(false)
                .comment(comment)
                .approverId(approverId)
                .approverName(approverName)
                .approvalTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建待审批的结果
     */
    public static ApprovalResult pending(String flowDefinitionId, String nextApproverId, int nextLevel) {
        return ApprovalResult.builder()
                .status("PENDING")
                .approved(false)
                .flowDefinitionId(flowDefinitionId)
                .nextApproverId(nextApproverId)
                .nextApprovalLevel(nextLevel)
                .approvalTime(LocalDateTime.now())
                .build();
    }
}