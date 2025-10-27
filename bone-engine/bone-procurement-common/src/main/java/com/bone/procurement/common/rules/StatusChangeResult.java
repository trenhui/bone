package com.bone.procurement.common.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态变更结果
 * 用于封装订单状态变更的处理结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusChangeResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 状态变更消息
     */
    private String message;
    
    /**
     * 原状态
     */
    private String oldStatus;
    
    /**
     * 新状态
     */
    private String newStatus;
    
    /**
     * 状态变更代码
     */
    private String resultCode;
    
    /**
     * 状态变更时间
     */
    private long timestamp;
    
    /**
     * 异常信息（如果有）
     */
    private String errorDetails;
}