package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class ClaimHangupRequest implements Serializable {

    @Schema(description = "待挂起赔案id")
    private Long claimId;

    @Schema(description = "挂起类型")
    private String hangupType;

    @Schema(description = "挂起原因")
    private String reason;

}
