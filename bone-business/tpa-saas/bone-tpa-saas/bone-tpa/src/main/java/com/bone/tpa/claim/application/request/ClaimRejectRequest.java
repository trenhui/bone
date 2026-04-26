package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class ClaimRejectRequest implements Serializable {

    @Schema(description = "待驳回赔案id")
    private Long claimId;

    @Schema(description = "错误类型")
    private String rejectType;

    @Schema(description = "错误原因")
    private String reason;

}
