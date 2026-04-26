package com.bone.tpa.claim.application.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 赔案挂起信息
 */
@Data
public class HangupInfo {

    @Schema(description = "退回人员")
    private String hangupOperator;

    @Schema(description = "退回时间")
    private Date hangupTime;

    @Schema(description = "退回差错类型")
    private String hangupType;

    @Schema(description = "退回差错原因")
    private String hangupReason;
}
