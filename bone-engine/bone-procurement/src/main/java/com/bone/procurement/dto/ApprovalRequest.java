package com.bone.procurement.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 审批请求DTO
 * 用于封装审批操作的请求参数
 */
@ApiModel(value = "ApprovalRequest", description = "审批请求参数")
public class ApprovalRequest {
    
    @ApiModelProperty(value = "审批意见/备注", required = false, example = "同意此订单")
    private String comments;
    
    @ApiModelProperty(value = "审批是否通过", required = false, example = "true")
    private boolean approved;
    
    public String getComments() {
        return comments;
    }
    
    public void setComments(String comments) {
        this.comments = comments;
    }
    
    public boolean isApproved() {
        return approved;
    }
    
    public void setApproved(boolean approved) {
        this.approved = approved;
    }
}