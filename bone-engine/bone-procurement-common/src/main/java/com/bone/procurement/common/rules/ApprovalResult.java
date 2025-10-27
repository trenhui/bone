package com.bone.procurement.common.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批结果
 * 用于封装审批规则执行的结果信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResult {
    
    /**
     * 是否批准
     */
    private boolean approved;
    
    /**
     * 审批意见
     */
    private String comment;
    
    /**
     * 审批流程ID
     */
    private String processId;
    
    /**
     * 当前审批节点
     */
    private String currentNode;
    
    /**
     * 下一审批节点
     */
    private String nextNode;
    
    /**
     * 审批人ID
     */
    private String approverId;
    
    /**
     * 审批人名称
     */
    private String approverName;
    
    /**
     * 审批结果代码
     */
    private String resultCode;
    
    /**
     * 审批时间
     */
    private long timestamp;
}