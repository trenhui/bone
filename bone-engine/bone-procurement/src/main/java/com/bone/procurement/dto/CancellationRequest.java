package com.bone.procurement.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 取消请求DTO
 * 用于封装取消订单操作的请求参数
 */
@ApiModel(value = "CancellationRequest", description = "订单取消请求参数")
public class CancellationRequest {
    
    @ApiModelProperty(value = "取消操作人ID", required = false, example = "1001")
    private Long operatorId;
    
    @ApiModelProperty(value = "取消原因", required = true, example = "预算调整")
    private String reason;
    
    public Long getOperatorId() {
        return operatorId;
    }
    
    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}